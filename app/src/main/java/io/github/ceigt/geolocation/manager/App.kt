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
import io.github.ceigt.geolocation.data.repository.PreferenceSync
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.data.repository.CredentialStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

class App : Application(), XposedServiceHelper.OnServiceListener {
    private var syncJob: Job? = null
    private var boundService: XposedService? = null
    private val serviceGeneration = java.util.concurrent.atomic.AtomicLong()
    companion object {
        private val syncRequests = Channel<Unit>(Channel.CONFLATED)
        private val _syncError = MutableStateFlow(false)
        val syncError = _syncError.asStateFlow()
        fun requestSync() { syncRequests.trySend(Unit) }
        private const val TAG = "GeoLocationApp"
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)   // exactly once
        applicationScope.launch {
            CredentialStore.migratePlaintext(getSharedPreferences(SHARED_PREFS_FILE, MODE_PRIVATE))
        }
    }

    @Synchronized
    override fun onServiceBind(service: XposedService) {
        boundService = service
        val generation = serviceGeneration.incrementAndGet()
        syncJob?.cancel()
        syncJob = applicationScope.launch {
            var imported = false
            var scopeSynced = false
            var retryDelay = 1000L
            while (isActive && generation == serviceGeneration.get()) {
                try {
                    val local = getSharedPreferences(SHARED_PREFS_FILE, MODE_PRIVATE)
                    val remote = service.getRemotePreferences(REMOTE_PREFS_GROUP)
                    if (!imported) {
                        PreferenceSync.bind(local, remote)
                        imported = true
                    } else PreferenceSync.flush(local, remote)
                    synchronized(this@App) {
                        if (generation != serviceGeneration.get()) return@launch
                        _serviceState.value = service
                        _syncError.value = false
                    }
                    if (!scopeSynced) {
                        scopeSynced = true
                        syncScopeToRemotePreferences(service)
                    }
                    retryDelay = 1000L
                    if (imported && local.getStringSet(PreferenceSync.PENDING, emptySet()).orEmpty().isEmpty()) {
                        syncRequests.receive()
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    synchronized(this@App) {
                        if (generation != serviceGeneration.get()) return@launch
                        if (!_syncError.value) Log.w(TAG, "Settings sync failed; retrying", error)
                        _syncError.value = true
                    }
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    @Synchronized
    override fun onServiceDied(service: XposedService) {
        if (boundService !== service) return
        boundService = null
        serviceGeneration.incrementAndGet()
        syncJob?.cancel()
        _serviceState.value = null
        _syncError.value = false
    }

    private fun syncScopeToRemotePreferences(
        service: XposedService
    ) {
        applicationScope.launch {
            val targetPackages = try {
                applicationHookTargets(service.scope).sorted()
            } catch (e: XposedService.ServiceException) {
                Log.w(TAG, "Failed to mirror LSPosed target scope", e)
                return@launch
            }

            if (App.service === service) PreferencesRepository(this@App).saveTargetApps(targetPackages.toSet())
        }
    }

}
