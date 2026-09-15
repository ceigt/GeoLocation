package io.github.ceigt.geolocation.xposed.hooks

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.IInterface
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.os.Bundle
import android.util.Log
import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal fun syntheticSecureLocationSetting(method: String?, setting: String?): String? {
    if (!method.equals("GET_secure", ignoreCase = true)) return null
    return when (setting) {
        "location_mode" -> "3"
        "location_providers_allowed" -> "gps,network"
        else -> null
    }
}

/** Android 12+ continuous listener supplement when the real location switch is off.
 * Native registration runs first: Android retains identity, request and permission validation.
 * No system settings are written and no proprietary implementation is included.
 */
internal class SystemActiveLocationHooks(private val module: XposedInterface, private val loader: ClassLoader) {
    private companion object {
        const val LOG_DELIVERY_DIAGNOSTICS = false
        const val CURRENT_CALLBACK_TTL_MS = 120_000L
    }

    private val tag = "[SystemActiveLocation]"
    private val handler by lazy { Handler(HandlerThread("GeoLocationSystemCallbacks").apply { start() }.looper) }
    private data class Key(val token: IBinder, val provider: String)
    private val registrationLock = Any()
    private val registrations = HashMap<Key, Registration>() // accessed only on handler
    private var previousActive = false
    private var announced = false
    private val providers = setOf("gps", "network", "fused", "passive")
    private val guarded = ConcurrentHashMap<Key, Registration>()
    private class CurrentRequest(val service: Any, val uid: Int, val pid: Int, val provider: String, val packageName: String)
    private val guardedCurrent = ConcurrentHashMap<IBinder, CurrentRequest>()
    private val guardedClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val nativeGuardReported = ConcurrentHashMap.newKeySet<String>()
    private val statusQueryReported = ConcurrentHashMap.newKeySet<String>()
    private val settingsQueryReported = ConcurrentHashMap.newKeySet<String>()
    private val settingsProviderClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val syntheticDispatch = ThreadLocal<Boolean>()
    private val nativeSource = ThreadLocal<Registration>()

    private inner class Registration(
        val listener: IInterface, val context: Context, val service: Any,
        val uid: Int, val pid: Int, val packageName: String, val provider: String,
        val callback: Method, val budget: SystemDeliveryBudget, val gnss: Boolean = false,
        minimumDistance: Float = 0f
    ) {
        val delivery = LocationDeliveryPolicy<android.location.Location>(budget, minimumDistance) { a, b -> a.distanceTo(b) }
        val token: IBinder = listener.asBinder()
        val key = Key(token, if (gnss) "_gnss" else provider)
        val death = IBinder.DeathRecipient { handler.post { remove(key, this) } }
        var cleanupAfter = 0L
        var enabledNotified = false
        var diagnosticState: String? = null
        var diagnosticCount = 0
        val availability = listener.javaClass.methods.firstOrNull {
            it.name == "onProviderEnabledChanged" && it.parameterTypes.contentEquals(
                arrayOf(String::class.java, Boolean::class.javaPrimitiveType))
        }?.apply { isAccessible = true }
    }

    /** Keep the native Binder token while retaining exact provider/generation provenance. */
    private inner class ProviderListenerHandler(val entry: Registration) : java.lang.reflect.InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val previous = nativeSource.get()
            nativeSource.set(entry)
            try {
                return method.invoke(entry.listener, *(args ?: emptyArray()))
            } catch (error: java.lang.reflect.InvocationTargetException) {
                throw error.targetException
            } finally {
                if (previous == null) nativeSource.remove() else nativeSource.set(previous)
            }
        }
    }

    private fun sourceOf(listener: IInterface?): Registration? {
        if (listener != null && java.lang.reflect.Proxy.isProxyClass(listener.javaClass)) {
            val source = java.lang.reflect.Proxy.getInvocationHandler(listener) as? ProviderListenerHandler
            if (source != null) return source.entry
        }
        return nativeSource.get()
    }

    private fun active(): Boolean = PreferencesUtil.snapshot().let { it.isPlaying && it.enableSystemHooks }

    fun initHooks() {
        if (Build.VERSION.SDK_INT < 31) return
        isolateHook(::reportFailure) { installNativeLocationGuard() }
        isolateHook(::reportFailure) { installNativeCurrentLocationGuard() }
        isolateHook(::reportFailure) { installNativeListenerTransportGuard() }
        isolateHook(::reportFailure) { installNativeCurrentTransportGuard() }
        val serviceClass = Class.forName("com.android.server.location.LocationManagerService", false, loader)
        for (name in listOf("isLocationEnabledForUser", "isProviderEnabledForUser")) {
            serviceClass.declaredMethods.filter { it.name == name && it.returnType == Boolean::class.javaPrimitiveType }
                .forEach { method -> isolateHook(::reportFailure) {
                    module.hook(method).intercept { chain ->
                        val uid = Binder.getCallingUid()
                        val result = chain.proceed() // preserve native user/permission checks
                        val context = contextOf(chain.thisObject)
                        val user = chain.args.filterIsInstance<Int>().lastOrNull()
                        val validProvider = name != "isProviderEnabledForUser" || chain.args.firstOrNull() in providers
                        val packageName = context?.let { appPackage(it, uid) }
                        val forceEnabled = active() && validProvider && user == uid / 100000 && packageName != null
                        if (forceEnabled && statusQueryReported.add("$packageName/$name")) {
                            module.log(Log.INFO, tag, "Location status query forced on: $packageName/$name")
                        }
                        if (forceEnabled) true else result
                    }
                } }
        }
        serviceClass.declaredMethods.filter { it.name == "registerLocationListener" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    synchronized(registrationLock) {
                    val uid = Binder.getCallingUid()
                    val pid = Binder.getCallingPid()
                    var prepared: Registration? = null
                    var previous: Registration? = null
                    // Do not capture system or manager registrations. Track application registrations
                    // even while paused, so a later start does not require a new native registration.
                    isolateHook(::reportFailure) {
                        val service = chain.thisObject
                        val context = contextOf(service)
                        val provider = chain.args.firstOrNull() as? String
                        val requestedPackage = chain.args.getOrNull(3) as? String
                        val validPackage = context?.let { appPackage(it, uid, requestedPackage) }
                        val listener = chain.args.filterIsInstance<IInterface>().firstOrNull {
                            it.asBinder().interfaceDescriptor == "android.location.ILocationListener"
                        }
                        if (service != null && context != null && validPackage != null &&
                            provider in providers && listener != null) {
                            val callback = listener.javaClass.methods.firstOrNull {
                                it.name == "onLocationChanged" && it.parameterTypes.size == 2 &&
                                    List::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                                    it.parameterTypes[1].name == "android.os.IRemoteCallback"
                            }
                            if (callback != null) {
                                callback.isAccessible = true
                                val request = chain.args.getOrNull(1)
                                val requestedInterval = number(request, "getIntervalMillis", 1000L)
                                val interval = if (provider == "passive") number(request, "getMinUpdateIntervalMillis", 1000L) else requestedInterval
                                if (interval != Long.MAX_VALUE) {
                                    val entry = Registration(listener, context, service, uid, pid, validPackage,
                                        provider!!, callback, SystemDeliveryBudget(SystemClock.elapsedRealtime(), interval,
                                            number(request, "getDurationMillis", Long.MAX_VALUE),
                                            number(request, "getMaxUpdates", Int.MAX_VALUE.toLong()).toInt()),
                                        minimumDistance = (request?.javaClass?.getMethod("getMinUpdateDistanceMeters")
                                            ?.invoke(request) as? Number)?.toFloat() ?: 0f)
                                    installAvailabilityGuard(entry)
                                    previous = guarded.put(entry.key, entry)
                                    prepared = entry
                                }
                            }
                        }
                    }
                    // Native registration still validates identity, permissions and request limits.
                    try {
                        val args = chain.args.toTypedArray()
                        prepared?.let { entry ->
                            val index = args.indexOfFirst { it === entry.listener }
                            val listenerType = Class.forName("android.location.ILocationListener", false, loader)
                            args[index] = java.lang.reflect.Proxy.newProxyInstance(listenerType.classLoader,
                                arrayOf(listenerType), ProviderListenerHandler(entry))
                        }
                        val result = chain.proceed(args)
                        prepared?.let { entry -> handler.post { add(entry) } }
                        result
                    } catch (error: Throwable) {
                        prepared?.let { entry ->
                            val old = previous
                            if (old == null) guarded.remove(entry.key, entry) else guarded.replace(entry.key, entry, old)
                        }
                        throw error
                    }
                    }
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name == "registerGnssStatusCallback" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    synchronized(registrationLock) {
                    val uid = Binder.getCallingUid()
                    val pid = Binder.getCallingPid()
                    val result = chain.proceed() // native identity and permission validation first
                    isolateHook(::reportFailure) {
                        val service = chain.thisObject ?: return@isolateHook
                        val context = contextOf(service) ?: return@isolateHook
                        val pkg = appPackage(context, uid, chain.args.getOrNull(1) as? String) ?: return@isolateHook
                        val listener = chain.args.filterIsInstance<IInterface>().firstOrNull {
                            it.asBinder().interfaceDescriptor == "android.location.IGnssStatusListener"
                        } ?: return@isolateHook
                        val callback = listener.javaClass.getMethod("onSvStatusChanged", GnssStatus::class.java)
                            .apply { isAccessible = true }
                        if (result != false) {
                            val entry = Registration(listener, context, service, uid, pid, pkg, "gps",
                                callback, SystemDeliveryBudget(SystemClock.elapsedRealtime(), 1000L, Long.MAX_VALUE, Int.MAX_VALUE), true)
                            guarded[entry.key] = entry
                            handler.post { add(entry) }
                        }
                    }
                    result
                    }
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name == "getCurrentLocation" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    val uid = Binder.getCallingUid()
                    val service = chain.thisObject
                    val context = contextOf(service)
                    val requestedPackage = chain.args.getOrNull(3) as? String
                    val packageName = context?.let { appPackage(it, uid, requestedPackage) }
                    val callback = chain.args.filterIsInstance<IInterface>().firstOrNull {
                        it.asBinder().interfaceDescriptor == "android.location.ILocationCallback"
                    }
                    val token = callback?.asBinder()
                    val current = if (packageName != null && service != null) CurrentRequest(service, uid,
                        Binder.getCallingPid(), chain.args.firstOrNull() as? String ?: "fused", packageName) else null
                    if (current != null && token != null) {
                        guardedCurrent[token] = current
                        // A cancelled or timed-out one-shot request may never invoke its Binder
                        // callback. Bound the tracking entry so repeated requests cannot retain
                        // callback binders for the lifetime of system_server.
                        handler.postDelayed({ guardedCurrent.remove(token, current) }, CURRENT_CALLBACK_TTL_MS)
                    }
                    try {
                        chain.proceed()
                    } catch (error: Throwable) {
                        if (token != null && current != null) guardedCurrent.remove(token, current)
                        throw error
                    }
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name in setOf("unregisterLocationListener", "unregisterGnssStatusCallback") }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    synchronized(registrationLock) {
                    val token = chain.args.filterIsInstance<IInterface>().firstOrNull()?.asBinder()
                    val result = chain.proceed()
                    if (token != null) {
                        guarded.values.filter { it.token == token }.forEach { entry ->
                            guarded.remove(entry.key, entry)
                            handler.post { remove(entry.key, entry) }
                        }
                    }
                    result
                    }
                }
            }
        }
        handler.post(tick)
        module.log(Log.INFO, tag, "Continuous system callbacks and availability adapters installed; device verification required")
    }

    fun onSettingsProviderReady(providerLoader: ClassLoader) {
        isolateHook(::reportFailure) { hookSettingsProviderQueries(providerLoader) }
    }

    private fun hookSettingsProviderQueries(providerLoader: ClassLoader) {
        val providerClass = Class.forName("com.android.providers.settings.SettingsProvider", false, providerLoader)
        if (!settingsProviderClasses.add(providerClass)) return
        providerClass.declaredMethods.filter { method ->
            method.name == "call" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java, Bundle::class.java)
            )
        }.forEach { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val replacement = syntheticSecureLocationSetting(
                    chain.args.getOrNull(0) as? String,
                    chain.args.getOrNull(1) as? String
                )
                if (!active() || replacement == null) return@intercept result

                val uid = Binder.getCallingUid()
                val context = (chain.thisObject as? android.content.ContentProvider)?.context
                val packageName = context?.let { appPackage(it, uid) } ?: return@intercept result
                val original = result as? Bundle ?: return@intercept result
                val copy = Bundle(original).apply { putString("value", replacement) }
                val setting = chain.args.getOrNull(1) as? String
                if (settingsQueryReported.add("$packageName/$setting")) {
                    module.log(Log.INFO, tag, "Secure location setting synthesized: $packageName/$setting")
                }
                copy
            }
        }
        module.log(Log.INFO, tag, "Secure location setting adapter installed")
    }

    private fun installAvailabilityGuard(entry: Registration) {
        val method = entry.availability ?: return
        if (!guardedClasses.add(entry.listener.javaClass)) return
        try {
            module.hook(method).intercept { chain ->
                val token = (chain.thisObject as? IInterface)?.asBinder()
                val tracked = sourceOf(chain.thisObject as? IInterface) ?: token?.let { findRegistration(it) }
                if (tracked != null && guarded[tracked.key] === tracked && active() && chain.args.firstOrNull() in providers && chain.args.getOrNull(1) == false) {
                    val args = chain.args.toTypedArray()
                    args[1] = true
                    chain.proceed(args)
                } else chain.proceed()
            }
        } catch (error: Throwable) {
            guardedClasses.remove(entry.listener.javaClass)
            throw error
        }
    }

    /**
     * Replace native coordinates at the last system-server boundary before Binder
     * serializes them into the client process. Hook the generated Proxy's declared
     * method directly: reflection through an ILocationListener instance may return
     * the interface Method, which does not execute when the Proxy overrides it.
     */
    private fun findRegistration(token: IBinder): Registration? {
        val entries = guarded.values.filter { it.token == token && !it.gnss }
        return entries.singleOrNull()
    }

    private fun acceptNative(entry: Registration, locations: List<android.location.Location>): List<android.location.Location> =
        synchronized(entry) {
            if (guarded[entry.key] !== entry) return@synchronized emptyList()
            val playing = active()
            val candidates = if (!playing) locations else locations.mapNotNull { location ->
                SystemLocationPrivacy.replace(entry.service, entry.uid, entry.pid, entry.provider,
                    location, entry.packageName, ::reportFailure)
            }
            entry.delivery.accept(SystemClock.elapsedRealtime(), candidates, playing)
        }

    private fun completeSuppressed(callback: Any?) {
        if (callback == null) return
        isolateHook(::reportFailure) {
            if (callback is Runnable) callback.run()
            else callback.javaClass.getMethod("sendResult", Bundle::class.java).apply { isAccessible = true }.invoke(callback, null)
        }
    }

    private fun installNativeLocationGuard() {
        val proxyClass = Class.forName("android.location.ILocationListener\$Stub\$Proxy", false, loader)
        val method = proxyClass.declaredMethods.single { candidate ->
            candidate.name == "onLocationChanged" && candidate.parameterTypes.size == 2 &&
                List::class.java.isAssignableFrom(candidate.parameterTypes[0]) &&
                candidate.parameterTypes[1].name == "android.os.IRemoteCallback"
        }.apply { isAccessible = true }
        module.hook(method).intercept { chain ->
            if (syntheticDispatch.get() == true) return@intercept chain.proceed()
            val token = (chain.thisObject as? IInterface)?.asBinder()
            val original = (chain.args.firstOrNull() as? List<*>)?.filterIsInstance<android.location.Location>()
            val tracked = sourceOf(chain.thisObject as? IInterface) ?: token?.let { findRegistration(it) }
            if (tracked == null || original.isNullOrEmpty()) return@intercept chain.proceed()
            val accepted = acceptNative(tracked, original)
            if (accepted.isEmpty()) {
                completeSuppressed(chain.args.getOrNull(1))
                return@intercept null
            }
            val args = chain.args.toTypedArray()
            args[0] = accepted
            chain.proceed(args)
        }
        module.log(Log.INFO, tag, "Native ILocationListener Binder guard installed")
    }

    /** One-shot getCurrentLocation uses a separate Binder interface. */
    private fun installNativeCurrentLocationGuard() {
        val proxyClass = Class.forName("android.location.ILocationCallback\$Stub\$Proxy", false, loader)
        val method = proxyClass.declaredMethods.single { candidate ->
            candidate.name == "onLocation" && candidate.parameterTypes.contentEquals(
                arrayOf(android.location.Location::class.java)
            )
        }.apply { isAccessible = true }
        module.hook(method).intercept { chain ->
            val token = (chain.thisObject as? IInterface)?.asBinder()
            val current = token?.let(guardedCurrent::remove)
            val packageName = current?.packageName
            if (packageName == null || !active() || chain.args.firstOrNull() == null) {
                chain.proceed()
            } else {
                val args = chain.args.toTypedArray()
                args[0] = SystemLocationPrivacy.replace(checkNotNull(current).service, current.uid, current.pid,
                    current.provider, chain.args.first() as android.location.Location, packageName, ::reportFailure)
                if (nativeGuardReported.add("$packageName/current")) {
                    module.log(Log.INFO, tag, "Native current-location payload replaced: $packageName")
                }
                chain.proceed(args)
            }
        }
        module.log(Log.INFO, tag, "Native ILocationCallback Binder guard installed")
    }

    /**
     * Android 15 delivers fixes through LocationProviderManager transports. This is
     * the common path used by framework clients even when the generated Binder Proxy
     * method is inlined or bypassed by the runtime.
     */
    private fun installNativeListenerTransportGuard() {
        val listenerTransport = Class.forName(
            "com.android.server.location.provider.LocationProviderManager\$LocationListenerTransport",
            false,
            loader
        )
        listenerTransport.declaredMethods.filter { it.name == "deliverOnLocationChanged" }.forEach { method ->
            module.hook(method).intercept { chain ->
                val listener = listenerTransport.getDeclaredField("mListener").apply { isAccessible = true }
                    .get(chain.thisObject) as? IInterface
                if (syntheticDispatch.get() == true) return@intercept chain.proceed()
                val original = chain.args.firstOrNull() ?: return@intercept chain.proceed()
                val locations = runCatching { original.javaClass.getMethod("asList").invoke(original) as? List<*> }
                    .getOrNull()?.filterIsInstance<android.location.Location>() ?: return@intercept chain.proceed()
                val tracked = sourceOf(listener) ?: listener?.asBinder()?.let { findRegistration(it) }
                    ?: return@intercept chain.proceed()
                val accepted = acceptNative(tracked, locations)
                if (accepted.isEmpty()) {
                    completeSuppressed(chain.args.getOrNull(1))
                    return@intercept null
                }
                val replacement = runCatching {
                    original.javaClass.getMethod("create", List::class.java).invoke(null, accepted)
                }.getOrElse { error ->
                    reportFailure(error)
                    completeSuppressed(chain.args.getOrNull(1))
                    return@intercept null
                }
                val args = chain.args.toTypedArray()
                args[0] = replacement
                syntheticDispatch.set(true)
                try { chain.proceed(args) } finally { syntheticDispatch.remove() }
            }
        }

    }

    private fun installNativeCurrentTransportGuard() {
        val currentTransport = Class.forName(
            "com.android.server.location.provider.LocationProviderManager\$GetCurrentLocationTransport",
            false,
            loader
        )
        currentTransport.declaredMethods.filter { it.name == "deliverOnLocationChanged" }.forEach { method ->
            module.hook(method).intercept { chain ->
                val callback = currentTransport.getDeclaredField("mCallback").apply { isAccessible = true }
                    .get(chain.thisObject) as? IInterface
                val token = callback?.asBinder()
                val current = token?.let(guardedCurrent::get)
                val packageName = current?.packageName
                val original = chain.args.firstOrNull()
                if (packageName == null || !active() || original == null) {
                    chain.proceed()
                } else {
                    val replacement = copyLocationResult(original, checkNotNull(current))
                    if (replacement == null) {
                        val args = chain.args.toTypedArray()
                        args[0] = null
                        chain.proceed(args)
                    } else {
                        val args = chain.args.toTypedArray()
                        args[0] = replacement
                        if (nativeGuardReported.add("$packageName/current-transport")) {
                            module.log(Log.INFO, tag, "Native current transport replaced: $packageName")
                        }
                        chain.proceed(args)
                    }
                }
            }
        }
        module.log(Log.INFO, tag, "Android 15 location transport guards installed")
    }

    private fun copyLocationResult(value: Any, current: CurrentRequest): Any? = runCatching {
        if (value.javaClass.name != "android.location.LocationResult") return@runCatching null
        val locations = value.javaClass.getMethod("asList").invoke(value) as? List<*> ?: return@runCatching null
        val replacements = locations.map { location ->
            SystemLocationPrivacy.replace(current.service, current.uid, current.pid, current.provider,
                location as android.location.Location, current.packageName, ::reportFailure) ?: return@runCatching null
        }
        value.javaClass.getMethod("create", List::class.java).invoke(null, replacements)
    }.onFailure(::reportFailure).getOrNull()

    private fun add(entry: Registration) {
        registrations[entry.key]?.takeIf { it !== entry }?.let { old ->
            runCatching { old.token.unlinkToDeath(old.death, 0) }
            registrations.remove(entry.key)
        }
        isolateHook({ guarded.remove(entry.key, entry); reportFailure(it) }) {
            entry.token.linkToDeath(entry.death, 0)
            if (guarded[entry.key] !== entry) {
                entry.token.unlinkToDeath(entry.death, 0)
                return@isolateHook
            }
            registrations[entry.key] = entry
            if (LOG_DELIVERY_DIAGNOSTICS) {
                module.log(Log.INFO, tag, "Listener retained: ${entry.packageName}/${entry.provider}")
            }
            deliver(entry, SystemClock.elapsedRealtime())
        }
    }

    private fun remove(key: Key, expected: Registration? = null) {
        val entry = guarded[key] ?: registrations[key] ?: return
        if (expected != null && entry !== expected) return
        registrations.remove(key)
        guarded.remove(key, entry)
        runCatching { entry.token.unlinkToDeath(entry.death, 0) }
    }

    private fun finishNativeRequest(entry: Registration, now: Long) = synchronized(registrationLock) {
        if (guarded[entry.key] !== entry || entry.gnss) return@synchronized
        try {
            val lookup = entry.service.javaClass.getDeclaredMethod("getLocationProviderManager", String::class.java)
                .apply { isAccessible = true }
            val manager = lookup.invoke(entry.service, entry.provider)
            if (manager != null) {
                val unregister = manager.javaClass.methods.first { it.name == "unregisterLocationRequest" &&
                    it.parameterTypes.size == 1 && it.parameterTypes[0].isInstance(entry.listener) }
                unregister.invoke(manager, entry.listener)
            }
            remove(entry.key, entry)
        } catch (error: Exception) {
            // Retain the guard rather than reviving an exhausted native request on an unknown ROM.
            entry.cleanupAfter = now + 60_000L
            reportFailure(error)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            isolateHook(::reportFailure) {
                val playing = active()
                if (playing != previousActive) {
                    previousActive = playing
                    isolateHook(::reportFailure) {
                        LocationManager::class.java.getDeclaredMethod("invalidateLocalLocationEnabledCaches").invoke(null)
                    }
                }
                val now = SystemClock.elapsedRealtime()
                registrations.values.toList().forEach { entry ->
                    if (!playing && entry.enabledNotified) {
                        isolateHook(::reportFailure) {
                            if (entry.gnss) {
                                if (!realLocationEnabled(entry)) gnssEvent(entry, "onGnssStopped")
                                return@isolateHook
                            }
                            val realEnabled = entry.service.javaClass.getMethod("isProviderEnabledForUser", String::class.java, Int::class.javaPrimitiveType)
                                .invoke(entry.service, entry.provider, entry.uid / 100000) as Boolean
                            entry.availability?.invoke(entry.listener, entry.provider, realEnabled)
                        }
                        entry.enabledNotified = false
                    }
                    if (!entry.token.isBinderAlive) {
                        remove(entry.key, entry)
                    } else if (entry.budget.expired(now) && now >= entry.cleanupAfter) {
                        finishNativeRequest(entry, now)
                    } else if (playing && entry.budget.due(now)) {
                        deliver(entry, now)
                    }
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun deliver(entry: Registration, now: Long) = synchronized(entry) {
        if (!active() || !entry.budget.due(now) || guarded[entry.key] !== entry) return@synchronized
        isolateHook({ remove(entry.key, entry); reportFailure(it) }) {
            // This call runs on our own thread with system identity, so the external
            // query adapter above leaves the actual switch value unchanged.
            val realEnabled = realLocationEnabled(entry)
            if (realEnabled) diagnostic(entry, "native-location-on")
            // Always provide a synthetic fix while simulation is active. When the real switch is
            // on, network identity guards intentionally remove Wi-Fi/cell positioning inputs;
            // waiting for a native fix in that state leaves indoor/vendor clients spinning.
            // Native callbacks remain protected by the transport and Binder guards above.
            if (mayDeliver(entry) && active()) {
                val location = if (entry.gnss) null else LocationUtil.createFakeLocation(
                    provider = if (entry.provider == "passive") "gps" else entry.provider, targetPackage = entry.packageName)
                val accepted = if (entry.gnss) entry.budget.acquire(now, 1, true) == 1
                    else entry.delivery.accept(now, listOf(checkNotNull(location)), true).isNotEmpty()
                if (!accepted) return@isolateHook
                if (!entry.enabledNotified) {
                    if (entry.gnss) {
                        gnssEvent(entry, "onGnssStarted")
                        entry.listener.javaClass.getMethod("onFirstFix", Int::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(entry.listener, 0)
                    } else entry.availability?.invoke(entry.listener, entry.provider, true)
                    entry.enabledNotified = true
                }
                if (entry.gnss) {
                    entry.callback.invoke(entry.listener, simulatedGnssStatus())
                } else {
                    syntheticDispatch.set(true)
                    try {
                        entry.callback.invoke(entry.listener, listOf(location), null)
                    } finally {
                        syntheticDispatch.remove()
                    }
                }
                diagnostic(entry, if (entry.gnss) "satellite-status-sent" else "callback-sent")
                if (!announced) {
                    announced = true
                    module.log(Log.INFO, tag, "Supplemental system callback sent")
                }
            }
        }
    }

    private fun mayDeliver(entry: Registration): Boolean {
        val context = entry.context
        val user = UserHandle.getUserHandleForUid(entry.uid)
        val users = context.getSystemService(UserManager::class.java) ?: return false
        if (users.isQuietModeEnabled(user)) return false
        val unlocked = UserManager::class.java.getMethod("isUserUnlocked", UserHandle::class.java)
            .invoke(users, user) as Boolean
        if (!unlocked) return false
        // Coarse-only clients remain on the native path; do not upgrade their permission.
        if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) {
            diagnostic(entry, "fine-permission-denied")
            return false
        }
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.noteOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, entry.uid, entry.packageName)
        val foreground = context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
            ?.any { it.uid == entry.uid && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND } == true
        if (!foreground && context.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) {
            diagnostic(entry, "background-permission-denied; appOp=$mode")
            return false
        }
        // Android applies a location-switch restriction to evaluated AppOps, including
        // checkOpRaw. For synthetic callbacks only, query stored authorization instead.
        // Never mutate AppOps or the real provider's restrictions.
        val savedAllowed = storedAuthorization(entry, appOps, foreground)
        val allowed = savedAllowed && (mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_FOREGROUND && foreground) || !realLocationEnabled(entry))
        if (!allowed) diagnostic(entry, "appOp-denied=$mode; foreground=$foreground")
        return allowed
    }

    private fun gnssEvent(entry: Registration, name: String) {
        entry.listener.javaClass.getMethod(name).apply { isAccessible = true }.invoke(entry.listener)
    }

    private fun simulatedGnssStatus(): GnssStatus = GnssStatus.Builder().apply {
        // Consistent simulated GPS sky; status only, no fabricated raw measurements/NMEA.
        for (index in 0 until 8) {
            addSatellite(GnssStatus.CONSTELLATION_GPS, index + 1, 32f + index,
                25f + index * 6, index * 45f, true, true, true, true, 1575.42e6f, false, 0f)
        }
    }.build()

    private fun storedAuthorization(entry: Registration, appOps: AppOpsManager, foreground: Boolean): Boolean {
        val user = UserHandle.getUserHandleForUid(entry.uid)
        val users = entry.context.getSystemService(UserManager::class.java) ?: return false
        val restrictions = UserManager::class.java.getMethod("getUserRestrictions", UserHandle::class.java)
            .invoke(users, user) as Bundle
        if (restrictions.getBoolean("no_share_location") || restrictions.getBoolean("no_config_location")) return false
        val pm = entry.context.packageManager
        val suspended = pm.javaClass.getMethod("isPackageSuspendedForUser", String::class.java, Int::class.javaPrimitiveType)
            .invoke(pm, entry.packageName, entry.uid / 100000) as Boolean
        if (suspended) return false
        val type = AppOpsManager::class.java
        val op = type.getMethod("strOpToOp", String::class.java).invoke(null, AppOpsManager.OPSTR_FINE_LOCATION) as Int
        val defaultMode = type.getMethod("opToDefaultMode", Int::class.javaPrimitiveType).invoke(null, op) as Int
        val service = type.getDeclaredField("mService").apply { isAccessible = true }.get(appOps)
        val uidOps = service.javaClass.getMethod("getUidOps", Int::class.javaPrimitiveType, IntArray::class.java)
            .invoke(service, entry.uid, intArrayOf(op))
        val packageOps = type.getMethod("getOpsForPackage", Int::class.javaPrimitiveType, String::class.java, IntArray::class.java)
            .invoke(appOps, entry.uid, entry.packageName, intArrayOf(op))
        fun savedMode(value: Any?): Int? {
            for (pkg in value as? List<*> ?: emptyList<Any>()) {
                if (pkg == null) continue
                val operations = pkg.javaClass.getMethod("getOps").invoke(pkg) as List<*>
                for (operation in operations.filterNotNull()) {
                    if (operation.javaClass.getMethod("getOp").invoke(operation) == op)
                        return operation.javaClass.getMethod("getMode").invoke(operation) as Int
                }
            }
            return null
        }
        return SyntheticLocationAccess.allowed(savedMode(uidOps), savedMode(packageOps), defaultMode, foreground)
    }

    // Bounded diagnostic state transitions only; never record coordinates or credentials.
    private fun diagnostic(entry: Registration, state: String) {
        if (!LOG_DELIVERY_DIAGNOSTICS) return
        if (entry.diagnosticState == state || entry.diagnosticCount >= 8) return
        entry.diagnosticState = state
        entry.diagnosticCount++
        module.log(Log.INFO, tag, "Delivery ${entry.packageName}/${entry.provider}: $state")
    }

    private fun realLocationEnabled(entry: Registration): Boolean =
        entry.service.javaClass.getMethod("isLocationEnabledForUser", Int::class.javaPrimitiveType)
            .invoke(entry.service, entry.uid / 100000) as Boolean

    private fun appPackage(context: Context, uid: Int, requested: String? = null): String? {
        if (uid % 100000 < 10000) return null
        val packages = context.packageManager.getPackagesForUid(uid)?.toSet() ?: return null
        if (MANAGER_APP_PACKAGE_NAME in packages) return null
        return if (requested != null) requested.takeIf { it in packages } else packages.firstOrNull()
    }

    private fun contextOf(service: Any?): Context? {
        var type: Class<*>? = service?.javaClass
        while (type != null) {
            try { return type.getDeclaredField("mContext").apply { isAccessible = true }.get(service) as? Context }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }

    private fun number(value: Any?, name: String, fallback: Long): Long =
        (value?.javaClass?.getMethod(name)?.invoke(value) as? Number)?.toLong() ?: fallback

    private fun reportFailure(error: Throwable) {
        module.log(Log.WARN, tag, "System callback adapter unavailable: ${error.javaClass.simpleName}")
    }
}
