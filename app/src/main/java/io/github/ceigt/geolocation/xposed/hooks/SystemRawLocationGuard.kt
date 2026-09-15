package io.github.ceigt.geolocation.xposed.hooks

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/** Suppresses real raw satellite data for app subscribers during system simulation.
 * Keeps native registration, permissions, status events and stop/start behavior intact.
 */
internal class SystemRawLocationGuard(private val module: XposedInterface, private val loader: ClassLoader) {
    private val callbacks = mapOf(
        "android.location.IGnssNmeaListener" to "onNmeaReceived",
        "android.location.IGnssMeasurementsListener" to "onGnssMeasurementsReceived",
        "android.location.IGnssNavigationMessageListener" to "onGnssNavigationMessageReceived"
    )
    private val tracked = ConcurrentHashMap<IBinder, IBinder.DeathRecipient>()
    private fun active() = PreferencesUtil.snapshot().let { it.isPlaying && it.enableSystemHooks }

    fun initHooks() {
        val installed = mutableSetOf<String>()
        for ((descriptor, callback) in callbacks) isolateHook(::failure) {
            val type = Class.forName(descriptor + "\$Stub\$Proxy", false, loader)
            val methods = type.declaredMethods.filter { it.name == callback && it.returnType == Void.TYPE }
            check(methods.isNotEmpty()) { "Missing raw callback" }
            for (method in methods) module.hook(method).intercept { chain ->
                val token = (chain.thisObject as? IInterface)?.asBinder()
                if (token != null && tracked.containsKey(token) && active()) null else chain.proceed()
            }
            installed += descriptor
        }
        val service = Class.forName("com.android.server.location.LocationManagerService", false, loader)
        for (method in service.declaredMethods.filter { it.name.contains("Gnss") }) isolateHook(::failure) {
            if (method.parameterTypes.none { it.name in installed }) return@isolateHook
            module.hook(method).intercept { chain ->
                val listener = chain.args.filterIsInstance<IInterface>().firstOrNull {
                    runCatching { it.asBinder().interfaceDescriptor in installed }.getOrDefault(false)
                } ?: return@intercept chain.proceed()
                val token = listener.asBinder()
                if (method.name.startsWith("unregister") || method.name.startsWith("remove")) {
                    val result = chain.proceed()
                    untrack(token)
                    return@intercept result
                }
                val uid = Binder.getCallingUid()
                val context = runCatching {
                    service.getDeclaredField("mContext").apply { isAccessible = true }.get(chain.thisObject) as Context
                }.getOrNull()
                val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
                if (uid % 100000 < 10000 || packages.isEmpty() || MANAGER_APP_PACKAGE_NAME in packages)
                    return@intercept chain.proceed()
                val death = IBinder.DeathRecipient { tracked.remove(token) }
                val added = tracked.putIfAbsent(token, death) == null
                try {
                    if (added) token.linkToDeath(death, 0)
                    val result = chain.proceed()
                    if (result == false && added) untrack(token)
                    result
                } catch (error: Throwable) {
                    if (added) untrack(token)
                    throw error
                }
            }
        }
        module.log(Log.INFO, TAG, "Raw GNSS guards installed: ${installed.size}/3; device verification required")
    }

    private fun untrack(token: IBinder) {
        tracked.remove(token)?.let { runCatching { token.unlinkToDeath(it, 0) } }
    }
    private fun failure(error: Throwable) = module.log(Log.WARN, TAG, "Raw GNSS guard unavailable: ${error.javaClass.simpleName}")
    private companion object { const val TAG = "[SystemRawLocation]" }
}
