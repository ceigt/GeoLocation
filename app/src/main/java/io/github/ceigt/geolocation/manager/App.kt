package io.github.ceigt.geolocation.manager

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import io.github.ceigt.geolocation.data.JsonCodec
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.KEY_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.KEY_ENABLE_SYSTEM_HOOKS
import io.github.ceigt.geolocation.data.KEY_TARGET_APPS
import io.github.ceigt.geolocation.data.REMOTE_PREFS_GROUP
import io.github.ceigt.geolocation.data.SHARED_PREFS_FILE
import io.github.ceigt.geolocation.data.applicationHookTargets
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private const val TAG = "GeoLocationApp"
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)   // exactly once
    }

    override fun onServiceBind(service: XposedService) {
        _serviceState.value = service
        val remotePrefs = service.getRemotePreferences(REMOTE_PREFS_GROUP)
        syncLocationModeToRemotePreferences(remotePrefs)
        syncScopeToRemotePreferences(service, remotePrefs)
    }

    override fun onServiceDied(service: XposedService) {
        _serviceState.value = null
    }

    private fun syncLocationModeToRemotePreferences(remotePrefs: SharedPreferences) {
        val localPrefs = getSharedPreferences(SHARED_PREFS_FILE, MODE_PRIVATE)
        remotePrefs.edit()
            .putBoolean(
                KEY_ENABLE_MOCK_PROVIDER,
                localPrefs.getBoolean(KEY_ENABLE_MOCK_PROVIDER, DEFAULT_ENABLE_MOCK_PROVIDER)
            )
            .apply()
    }

    private fun syncScopeToRemotePreferences(
        service: XposedService,
        remotePrefs: SharedPreferences
    ) {
        applicationScope.launch {
            val targetPackages = try {
                applicationHookTargets(service.scope).sorted()
            } catch (e: XposedService.ServiceException) {
                Log.w(TAG, "Failed to mirror LSPosed target scope", e)
                return@launch
            }

            remotePrefs.edit()
                .putString(KEY_TARGET_APPS, JsonCodec.encodeStrings(targetPackages))
                // A system-only scope cannot intercept vendor SDK objects created inside apps.
                // Reset an invalid 1.2.5 configuration so the manager prompts for an app target.
                .apply {
                    if (targetPackages.isEmpty()) putBoolean(KEY_ENABLE_SYSTEM_HOOKS, false)
                }
                .apply()
        }
    }

}
