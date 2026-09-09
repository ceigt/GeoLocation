package io.github.ceigt.geolocation.xposed.hooks

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.IInterface
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

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
    private val providers = setOf("gps", "network", "fused")

    private inner class Registration(
        val listener: IInterface, val context: Context, val service: Any,
        val uid: Int, val pid: Int, val packageName: String, val provider: String,
        val callback: Method, val budget: SystemDeliveryBudget
    ) {
        val token: IBinder = listener.asBinder()
        val death = IBinder.DeathRecipient { handler.post { remove(token, this) } }
        var enabledNotified = false
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
                    val result = chain.proceed()
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
                                val interval = number(request, "getIntervalMillis", 1000L)
                                if (interval != Long.MAX_VALUE) {
                                    val entry = Registration(listener, context, service, uid, pid, validPackage,
                                        provider!!, callback, SystemDeliveryBudget(SystemClock.elapsedRealtime(), interval,
                                            number(request, "getDurationMillis", Long.MAX_VALUE),
                                            number(request, "getMaxUpdates", Int.MAX_VALUE.toLong()).toInt()))
                                    handler.post { add(entry) }
                                }
                            }
                        }
                    }
                    result
                }
            }
        }
        serviceClass.declaredMethods.filter { it.name == "unregisterLocationListener" }.forEach { method ->
            isolateHook(::reportFailure) {
                module.hook(method).intercept { chain ->
                    val token = chain.args.filterIsInstance<IInterface>().firstOrNull()?.asBinder()
                    val result = chain.proceed()
                    if (token != null) handler.post { remove(token) }
                    result
                }
            }
        }
        handler.post(tick)
        module.log(Log.INFO, tag, "Continuous system callbacks and availability adapters installed; device verification required")
    }

    private fun add(entry: Registration) {
        remove(entry.token)
        isolateHook(::reportFailure) {
            entry.token.linkToDeath(entry.death, 0)
            registrations[entry.token] = entry
        }
    }

    private fun remove(token: IBinder, expected: Registration? = null) {
        val entry = registrations[token] ?: return
        if (expected != null && entry !== expected) return
        registrations.remove(token)
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
                            val realEnabled = entry.service.javaClass.getMethod("isProviderEnabledForUser", String::class.java, Int::class.javaPrimitiveType)
                                .invoke(entry.service, entry.provider, entry.uid / 100000) as Boolean
                            entry.availability?.invoke(entry.listener, entry.provider, realEnabled)
                        }
                        entry.enabledNotified = false
                    }
                    if (!entry.token.isBinderAlive || entry.budget.expired(now)) {
                        remove(entry.token, entry)
                    } else if (playing && entry.budget.due(now)) {
                        isolateHook({ remove(entry.token, entry); reportFailure(it) }) {
                            // This call runs on our own thread with system identity, so the external
                            // query adapter above leaves the actual switch value unchanged.
                            val realEnabled = realLocationEnabled(entry)
                            if (!realEnabled && mayDeliver(entry) && active()) {
                                if (!entry.enabledNotified) {
                                    entry.availability?.invoke(entry.listener, entry.provider, true)
                                    entry.enabledNotified = true
                                }
                                val location = LocationUtil.createFakeLocation(provider = entry.provider, targetPackage = entry.packageName)
                                entry.callback.invoke(entry.listener, listOf(location), null)
                                entry.budget.sent(now)
                                if (!announced) {
                                    announced = true
                                    module.log(Log.INFO, tag, "Supplemental callback sent while real location is off")
                                }
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun mayDeliver(entry: Registration): Boolean {
        val context = entry.context
        // Coarse-only clients remain on the native path; do not upgrade their permission.
        if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, entry.uid, entry.packageName)
        val foreground = context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
            ?.any { it.uid == entry.uid && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE } == true
        if (!foreground && context.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, entry.pid, entry.uid) != PackageManager.PERMISSION_GRANTED) return false
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_FOREGROUND && foreground)
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
