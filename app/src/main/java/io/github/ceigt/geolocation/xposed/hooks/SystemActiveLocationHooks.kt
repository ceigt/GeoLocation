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

/** Android 12+ continuous listener supplement when the real location switch is off.
 * Native registration runs first: Android retains identity, request and permission validation.
 * No system settings are written and no proprietary implementation is included.
 */
internal class SystemActiveLocationHooks(private val module: XposedInterface, private val loader: ClassLoader) {
    private val tag = "[SystemActiveLocation]"
    private val handler by lazy { Handler(HandlerThread("GeoLocationSystemCallbacks").apply { start() }.looper) }
    private val registrations = HashMap<IBinder, Registration>() // accessed only on handler
    private var previousActive = false
    private var announced = false
    private val providers = setOf("gps", "network", "fused", "passive")
    private val guarded = ConcurrentHashMap<IBinder, Registration>()
    private val guardedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    private inner class Registration(
        val listener: IInterface, val context: Context, val service: Any,
        val uid: Int, val pid: Int, val packageName: String, val provider: String,
        val callback: Method, val budget: SystemDeliveryBudget, val gnss: Boolean = false
    ) {
        val token: IBinder = listener.asBinder()
        val death = IBinder.DeathRecipient { handler.post { remove(token, this) } }
        var enabledNotified = false
        var diagnosticState: String? = null
        var diagnosticCount = 0
        val availability = listener.javaClass.methods.firstOrNull {
            it.name == "onProviderEnabledChanged" && it.parameterTypes.contentEquals(
                arrayOf(String::class.java, Boolean::class.javaPrimitiveType))
        }?.apply { isAccessible = true }
    }

    private fun active(): Boolean = PreferencesUtil.snapshot().let { it.isPlaying && it.enableSystemHooks }

    fun initHooks() {
        if (Build.VERSION.SDK_INT < 31) return
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
                        if (active() && context != null && validProvider && user == uid / 100000 &&
                            appPackage(context, uid) != null) true else result
                    }
                } }
        }
        serviceClass.declaredMethods.filter { it.name == "registerLocationListener" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    val uid = Binder.getCallingUid()
                    val pid = Binder.getCallingPid()
                    var prepared: Registration? = null
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
                                            number(request, "getMaxUpdates", Int.MAX_VALUE.toLong()).toInt()))
                                    installAvailabilityGuard(entry)
                                    guarded[entry.token] = entry
                                    prepared = entry
                                }
                            }
                        }
                    }
                    // Native registration still validates identity, permissions and request limits.
                    try {
                        val result = chain.proceed()
                        prepared?.let { entry -> handler.post { add(entry) } }
                        result
                    } catch (error: Throwable) {
                        prepared?.let { guarded.remove(it.token, it) }
                        throw error
                    }
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name == "registerGnssStatusCallback" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
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
                            guarded[entry.token] = entry
                            handler.post { add(entry) }
                        }
                    }
                    result
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name in setOf("unregisterLocationListener", "unregisterGnssStatusCallback") }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    val token = chain.args.filterIsInstance<IInterface>().firstOrNull()?.asBinder()
                    val result = chain.proceed()
                    if (token != null) {
                        val removed = guarded.remove(token)
                        if (removed != null) handler.post { remove(token, removed) }
                    }
                    result
                }
            }
        }
        handler.post(tick)
        module.log(Log.INFO, tag, "Continuous system callbacks and availability adapters installed; device verification required")
    }

    private fun installAvailabilityGuard(entry: Registration) {
        val method = entry.availability ?: return
        if (!guardedClasses.add(entry.listener.javaClass)) return
        try {
            module.hook(method).intercept { chain ->
                val token = (chain.thisObject as? IInterface)?.asBinder()
                val tracked = token?.let(guarded::get)
                if (tracked != null && active() && chain.args.firstOrNull() in providers && chain.args.getOrNull(1) == false) {
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

    private fun add(entry: Registration) {
        remove(entry.token)
        isolateHook({ guarded.remove(entry.token, entry); reportFailure(it) }) {
            entry.token.linkToDeath(entry.death, 0)
            if (guarded[entry.token] !== entry) {
                entry.token.unlinkToDeath(entry.death, 0)
                return@isolateHook
            }
            registrations[entry.token] = entry
            module.log(Log.INFO, tag, "Listener retained: ${entry.packageName}/${entry.provider}")
            deliver(entry, SystemClock.elapsedRealtime())
        }
    }

    private fun remove(token: IBinder, expected: Registration? = null) {
        val entry = registrations[token] ?: return
        if (expected != null && entry !== expected) return
        registrations.remove(token)
        guarded.remove(token, entry)
        runCatching { token.unlinkToDeath(entry.death, 0) }
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
                    if (!entry.token.isBinderAlive || entry.budget.expired(now)) {
                        remove(entry.token, entry)
                    } else if (playing && entry.budget.due(now)) {
                        deliver(entry, now)
                    }
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun deliver(entry: Registration, now: Long) {
        if (!active() || !entry.budget.due(now) || guarded[entry.token] !== entry) return
        isolateHook({ remove(entry.token, entry); reportFailure(it) }) {
            // This call runs on our own thread with system identity, so the external
            // query adapter above leaves the actual switch value unchanged.
            val realEnabled = realLocationEnabled(entry)
            if (realEnabled) diagnostic(entry, "native-location-on")
            if (!realEnabled && mayDeliver(entry) && active()) {
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
                    val location = LocationUtil.createFakeLocation(provider = if (entry.provider == "passive") "gps" else entry.provider, targetPackage = entry.packageName)
                    entry.callback.invoke(entry.listener, listOf(location), null)
                }
                diagnostic(entry, if (entry.gnss) "satellite-status-sent" else "callback-sent")
                entry.budget.sent(now)
                if (!announced) {
                    announced = true
                    module.log(Log.INFO, tag, "Supplemental callback sent while real location is off")
                }
            }
        }
    }

    private fun mayDeliver(entry: Registration): Boolean {
        val context = entry.context
        // Coarse-only clients remain on the native path; do not upgrade their permission.
        if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) {
            diagnostic(entry, "fine-permission-denied")
            return false
        }
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, entry.uid, entry.packageName)
        val foreground = context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
            ?.any { it.uid == entry.uid && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE } == true
        if (!foreground && context.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) {
            diagnostic(entry, "background-permission-denied; appOp=$mode")
            return false
        }
        // Android applies a location-switch restriction to evaluated AppOps, including
        // checkOpRaw. For synthetic callbacks only, query stored authorization instead.
        // Never mutate AppOps or the real provider's restrictions.
        val allowed = if (mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_FOREGROUND && foreground)) true
            else !realLocationEnabled(entry) && storedAuthorization(entry, appOps, foreground)
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
