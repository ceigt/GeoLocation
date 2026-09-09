package io.github.ceigt.geolocation.xposed.hooks

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Application-process hook orchestrator.
 *
 * Each child module owns one location concern so compatibility fixes can be made without
 * changing unrelated hooks. Keep the initialization order stable because libxposed installs
 * interceptors in call order.
 */
class LocationApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationApiHooks]"
    private val tencent = TencentLocationHooks(module, classLoader)

    fun initHooks() {
        initialize("Location objects") { LocationObjectHooks(module, classLoader).initHooks() }
        initialize("Location manager") { LocationManagerHooks(module, classLoader).initHooks() }
        initialize("Active callbacks") { ActiveLocationHooks(module).initHooks() }
        initialize("Callback results") { LocationCallbackHooks(module, classLoader).initHooks() }
        initialize("Tencent SDK") { tencent.initHooks() }
        // Keep Wi-Fi scans, cell information and GNSS callback registration available. Tencent,
        // AMap and other vendor location SDKs use those sources to establish a fix before they
        // emit an Android Location. Clearing the sources here can suppress location callbacks
        // entirely. Location payload and mock-origin hooks below still replace the reported fix.
        module.log(Log.INFO, tag, "Hook setup finished; callback delivery requires runtime verification")
    }

    fun onApplicationReady(loader: ClassLoader) {
        initialize("Tencent SDK retry") { tencent.initHooks(loader) }
    }

    private inline fun initialize(name: String, action: () -> Unit) {
        isolateHook({ module.log(Log.WARN, tag, "$name unavailable: ${it.javaClass.simpleName}") }, action)
    }
}
