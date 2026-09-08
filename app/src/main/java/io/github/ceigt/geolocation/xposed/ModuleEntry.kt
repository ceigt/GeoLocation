package io.github.ceigt.geolocation.xposed

import android.app.Application
import android.util.Log
import android.widget.Toast
import io.github.ceigt.geolocation.data.REMOTE_PREFS_GROUP
import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import io.github.ceigt.geolocation.xposed.hooks.LocationApiHooks
import io.github.ceigt.geolocation.xposed.hooks.SystemServicesHooks
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleEntry : XposedModule() {
    companion object {
        const val TAG = "[ModuleEntry]"
        private const val PHONE_PACKAGE = "com.android.phone"
    }

    private var locationApiHooks: LocationApiHooks? = null
    private var systemServicesHooks: SystemServicesHooks? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        LocationUtil.logger = { priority, tag, message -> log(priority, tag, message) }
        PreferencesUtil.logger = { priority, tag, message -> log(priority, tag, message) }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
        log(Log.INFO, TAG, "\tdefault classloder: ${param.defaultClassLoader}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "onPackageReady: ${param.packageName}")
        log(Log.INFO, TAG, "\tapp classloder: ${param.classLoader}")
        log(Log.INFO, TAG, "\tmodule apk path: ${moduleApplicationInfo.sourceDir}")

        // Run per-package setup only once.
        if (!param.isFirstPackage) return

        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
        LocationUtil.targetPackageName = param.packageName

        if (param.packageName == PHONE_PACKAGE) {
            // Keep telephony callbacks intact. Tencent and other network-location clients need
            // cell updates to establish a fix; clearing them can prevent any location result.
            // We still skip app-level location hooks in the phone process itself.
            log(Log.INFO, TAG, "Skipping hooks for the telephony process in compatibility mode.")
        } else if (param.packageName == MANAGER_APP_PACKAGE_NAME) {
            // The manager must always read the device's real location for the “My location” map
            // control. This also protects users who accidentally add the manager to Xposed scope.
            log(Log.INFO, TAG, "Skipping location hooks for the manager app.")
        } else {
            initHookingLogic(param)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting:\n\t${param.classLoader}")

        // system_server is a hooked process only when the user enabled system-level hooks (which adds
        // "system"/"android" to the module scope). Per-intercept isPlaying + target_apps gating keeps these
        // inert until the user is actively spoofing a selected target.
        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
        LocationUtil.targetPackageName = "system"
        systemServicesHooks = SystemServicesHooks(this, param.classLoader).also { it.initHooks() }
    }

    private fun initHookingLogic(param: PackageReadyParam) {
        // Install location hooks as soon as the package class loader is ready. Waiting until after
        // Application.onCreate lets apps such as WeChat register vendor location listeners before
        // GeoLocation can wrap them, so those callbacks continue exposing the real location.
        locationApiHooks = LocationApiHooks(this, param.classLoader).also { it.initHooks() }

        val clazz = Class.forName("android.app.Instrumentation", false, param.classLoader)
        val method = clazz.getDeclaredMethod("callApplicationOnCreate", Application::class.java)

        hook(method).intercept { chain ->
            val result = chain.proceed()

            try {
                val context = (chain.getArg(0) as Application).applicationContext
                locationApiHooks?.onApplicationReady(context.classLoader)
                log(Log.INFO, TAG, "Target App's context has been acquired (${param.packageName}).")
                if (PreferencesUtil.getIsPlaying() && PreferencesUtil.getHideFakeLocationToast() != true) {
                    Toast.makeText(context, "Fake Location Is Active!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Toast/context failed - ${e.message}")
            }
            result
        }
    }
}
