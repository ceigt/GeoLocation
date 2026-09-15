package io.github.ceigt.geolocation.manager.mock

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import io.github.ceigt.geolocation.data.JsonCodec
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.DEFAULT_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.KEY_IS_PLAYING
import io.github.ceigt.geolocation.data.KEY_LAST_CLICKED_LOCATION
import io.github.ceigt.geolocation.data.KEY_USE_ACCURACY
import io.github.ceigt.geolocation.data.SHARED_PREFS_FILE
import io.github.ceigt.geolocation.data.model.LastClickedLocation
import java.util.concurrent.TimeUnit
import io.github.ceigt.geolocation.manager.control.RootCommands
import io.github.ceigt.geolocation.data.repository.PreferenceSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockLocationService : Service() {
    private lateinit var handler: Handler
    @Volatile private var destroyed = false
    @Volatile private var latestStartId = 0
    private lateinit var locationManager: LocationManager
    private lateinit var powerManager: PowerManager
    private lateinit var preferences: SharedPreferences
    @Volatile private var currentState = MockState(false, null, DEFAULT_MOCK_ACCURACY_METERS)
    private var mockLocationRepairAttempted = false
    private val readyProviders = mutableSetOf<String>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        currentState = readState(prefs)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (destroyed) return
            val startId = latestStartId
            val state = currentState
            if (!state.canSpoof || state.location == null) {
                stopMockProviders()
                stopSelf(startId)
                return
            }

            if (pushMockLocation(state.location, state.accuracy)) {
                if (destroyed || startId != latestStartId) return
                runtimeState.value = RuntimeState.RUNNING
                val interval = if (powerManager.isInteractive) {
                    FOREGROUND_UPDATE_INTERVAL_MS
                } else {
                    SCREEN_OFF_UPDATE_INTERVAL_MS
                }
                handler.postDelayed(this, interval)
            } else {
                android.os.Handler(mainLooper).post {
                    if (!destroyed && startId == latestStartId) {
                        recordFailure(this@MockLocationService)
                        stopSelf(startId)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = worker
        locationManager = getSystemService(LocationManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        preferences = getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        currentState = readState(preferences)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            else -> {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (error: Exception) {
                    recordFailure(this)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                currentState = readState(preferences)
                handler.removeCallbacks(tick)
                handler.post {
                    if (!destroyed && latestStartId == startId) {
                        mockLocationRepairAttempted = false
                        tick.run()
                    }
                }
                // Compatibility mode is explicitly enabled by the user and must keep feeding
                // repeated location requests even if Android recreates this foreground service.
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(tick)
        // Provider mutations from successive service instances share one serial worker.
        handler.post { stopMockProviders() }
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (runtimeState.value != RuntimeState.ERROR) runtimeState.value = RuntimeState.STOPPED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readState(prefs: SharedPreferences = preferences): MockState {
        val canSpoof = prefs.getBoolean(KEY_IS_PLAYING, false) &&
            prefs.getBoolean(KEY_ENABLE_MOCK_PROVIDER, false)

        val location = prefs.getString(KEY_LAST_CLICKED_LOCATION, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JsonCodec.decodeLocation(it) }.getOrNull() }
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }

        val accuracy = if (prefs.getBoolean(KEY_USE_ACCURACY, false)) {
            readDouble(prefs, KEY_ACCURACY, DEFAULT_ACCURACY).toFloat()
        } else {
            DEFAULT_ACCURACY.toFloat()
        }.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_MOCK_ACCURACY_METERS

        return MockState(canSpoof = canSpoof, location = location, accuracy = accuracy)
    }

    private fun pushMockLocation(location: LastClickedLocation, accuracy: Float): Boolean {
        if (pushMockLocationOnce(location, accuracy)) return true

        if (!mockLocationRepairAttempted && repairMockLocationAppOp()) {
            return pushMockLocationOnce(location, accuracy)
        }
        return false
    }

    private fun pushMockLocationOnce(location: LastClickedLocation, accuracy: Float): Boolean {
        if (destroyed || !currentState.canSpoof) return false
        var pushed = false
        TARGET_PROVIDERS.forEach { provider ->
            val providerReady = ensureMockProvider(provider)
            if (providerReady) {
                runCatching {
                    locationManager.setTestProviderLocation(provider, buildLocation(provider, location, accuracy))
                    pushed = true
                }.onFailure {
                    Log.w(TAG, "Could not set mock location for $provider: ${it.message}")
                }
            }
        }
        return pushed
    }

    private fun repairMockLocationAppOp(): Boolean {
        mockLocationRepairAttempted = true
        return !destroyed && RootCommands.enableMockLocation(packageName)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
    private fun ensureMockProvider(provider: String): Boolean {
        if (provider in readyProviders) return true

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(provider, providerProperties())
            } else {
                locationManager.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
            }
        }.recoverCatching {
            // Existing mock providers throw on add; enabling and setting below is enough.
        }.mapCatching {
            locationManager.setTestProviderEnabled(provider, true)
            readyProviders += provider
            true
        }.onFailure {
            Log.e(TAG, "Mock provider $provider is not available: ${it.message}")
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun providerProperties(): ProviderProperties {
        return ProviderProperties.Builder()
            .setAccuracy(ProviderProperties.ACCURACY_FINE)
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .build()
    }

    private fun buildLocation(
        provider: String,
        location: LastClickedLocation,
        accuracy: Float
    ): Location {
        return Location(provider).apply {
            latitude = location.latitude
            longitude = location.longitude
            this.accuracy = accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun stopMockProviders() {
        TARGET_PROVIDERS.forEach { provider ->
            runCatching { locationManager.removeTestProvider(provider) }
        }
        readyProviders.clear()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.mock_provider_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.mock_provider_notification_text))
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun readDouble(
        prefs: android.content.SharedPreferences,
        key: String,
        default: Double
    ): Double {
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    private data class MockState(
        val canSpoof: Boolean,
        val location: LastClickedLocation?,
        val accuracy: Float
    )

    companion object {
        enum class RuntimeState { STOPPED, STARTING, RUNNING, ERROR }
        private val runtimeState = MutableStateFlow(RuntimeState.STOPPED)
        val state = runtimeState.asStateFlow()
        private val worker by lazy { Handler(HandlerThread("GeoLocationMockProvider").apply { start() }.looper) }

        private fun recordFailure(context: Context) {
            runtimeState.value = RuntimeState.ERROR
            val prefs = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            PreferenceSync.edit(prefs, null) { putBoolean(KEY_IS_PLAYING, false) }
            io.github.ceigt.geolocation.manager.App.requestSync()
        }
        private const val TAG = "MockLocationService"
        private const val ACTION_START = "io.github.ceigt.geolocation.action.MOCK_PROVIDER_START"
        private const val ACTION_STOP = "io.github.ceigt.geolocation.action.MOCK_PROVIDER_STOP"
        private const val CHANNEL_ID = "geolocation_mock_provider"
        private const val NOTIFICATION_ID = 2001
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1_000L
        private const val SCREEN_OFF_UPDATE_INTERVAL_MS = 5_000L
        private const val DEFAULT_MOCK_ACCURACY_METERS = 5f
        private const val ROOT_REPAIR_TIMEOUT_SECONDS = 8L

        private val TARGET_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            FUSED_PROVIDER
        )

        private const val FUSED_PROVIDER = "fused"

        fun sync(context: Context, enabled: Boolean) {
            val appContext = context.applicationContext
            try {
                if (enabled) {
                    runtimeState.value = RuntimeState.STARTING
                    val intent = Intent(appContext, MockLocationService::class.java).setAction(ACTION_START)
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    runtimeState.value = RuntimeState.STOPPED
                    appContext.stopService(Intent(appContext, MockLocationService::class.java))
                }
            } catch (e: Exception) {
                if (enabled) recordFailure(appContext)
                Log.e(TAG, "Could not sync mock provider service: ${e.message}")
            }
        }
    }
}
