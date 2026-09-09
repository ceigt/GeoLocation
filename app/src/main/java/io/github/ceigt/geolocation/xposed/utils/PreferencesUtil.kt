// PreferencesUtil.kt
package io.github.ceigt.geolocation.xposed.utils

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.data.JsonCodec
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.data.DEFAULT_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_ALTITUDE
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import io.github.ceigt.geolocation.data.DEFAULT_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_RANDOMIZE_RADIUS
import io.github.ceigt.geolocation.data.DEFAULT_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_ALTITUDE
import io.github.ceigt.geolocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_RANDOMIZE
import io.github.ceigt.geolocation.data.DEFAULT_USE_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_USE_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_ACCURACY
import io.github.ceigt.geolocation.data.KEY_APP_COORDINATE_SYSTEMS
import io.github.ceigt.geolocation.data.KEY_ALTITUDE
import io.github.ceigt.geolocation.data.KEY_ENABLE_SYSTEM_HOOKS
import io.github.ceigt.geolocation.data.KEY_ENABLE_MOCK_PROVIDER
import io.github.ceigt.geolocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import io.github.ceigt.geolocation.data.KEY_IS_PLAYING
import io.github.ceigt.geolocation.data.KEY_LAST_CLICKED_LOCATION
import io.github.ceigt.geolocation.data.KEY_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.KEY_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_RANDOMIZE_RADIUS
import io.github.ceigt.geolocation.data.KEY_SPEED
import io.github.ceigt.geolocation.data.KEY_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.KEY_TARGET_APPS
import io.github.ceigt.geolocation.data.KEY_USE_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_ALTITUDE
import io.github.ceigt.geolocation.data.KEY_USE_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.KEY_USE_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_RANDOMIZE
import io.github.ceigt.geolocation.data.KEY_USE_SPEED
import io.github.ceigt.geolocation.data.KEY_USE_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.KEY_USE_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.KEY_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.model.LastClickedLocation

object PreferencesUtil {
    private const val TAG = "[PreferencesUtil]"

    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)


    @Volatile private var preferences: SharedPreferences? = null
    @Volatile private var registeredPrefs: SharedPreferences? = null
    @Volatile private var cache: PreferencesSnapshot = PreferencesSnapshot()
    @Volatile private var lastRefreshNanos: Long = 0L
    private var lastDiagnostic: String? = null

    // IMPORTANT: keep a strong reference. SharedPreferences holds listeners *weakly*,
    // so a listener that isn't referenced anywhere gets GC'd and silently stops firing.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        refreshCache(prefs)
    }

    fun init(prefs: SharedPreferences) {
        preferences = prefs
        if (registeredPrefs !== prefs) {
            registeredPrefs?.let {
                runCatching { it.unregisterOnSharedPreferenceChangeListener(changeListener) }
            }
            prefs.registerOnSharedPreferenceChangeListener(changeListener)
            registeredPrefs = prefs
        }
        refreshCache(prefs)
        log("Initialized with cached remote preferences")
    }

    fun refresh() {
        refreshCache()
    }

    fun getIsPlaying(): Boolean {
        return snapshot().isPlaying
    }

    fun snapshot(): PreferencesSnapshot {
        maybeRefreshCache()
        return cache
    }

    fun getLastClickedLocation(): LastClickedLocation? = cache.lastClickedLocation
    fun getUseAccuracy(): Boolean = cache.useAccuracy
    fun getAccuracy(): Double = cache.accuracy
    fun getUseAltitude(): Boolean = cache.useAltitude
    fun getAltitude(): Double = cache.altitude
    fun getUseRandomize(): Boolean = cache.useRandomize
    fun getRandomizeRadius(): Double = cache.randomizeRadius
    fun getUseVerticalAccuracy(): Boolean = cache.useVerticalAccuracy
    fun getVerticalAccuracy(): Float = cache.verticalAccuracy
    fun getUseMeanSeaLevel(): Boolean = cache.useMeanSeaLevel
    fun getMeanSeaLevel(): Double = cache.meanSeaLevel
    fun getUseMeanSeaLevelAccuracy(): Boolean = cache.useMeanSeaLevelAccuracy
    fun getMeanSeaLevelAccuracy(): Float = cache.meanSeaLevelAccuracy
    fun getUseSpeed(): Boolean = cache.useSpeed
    fun getSpeed(): Float = cache.speed
    fun getUseSpeedAccuracy(): Boolean = cache.useSpeedAccuracy
    fun getSpeedAccuracy(): Float = cache.speedAccuracy
    fun getHideFakeLocationToast(): Boolean = cache.hideFakeLocationToast
    fun getEnableSystemHooks(): Boolean = cache.enableSystemHooks
    fun getTargetApps(): Set<String> {
        return snapshot().targetApps
    }

    private fun maybeRefreshCache() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastRefreshNanos < REFRESH_INTERVAL_NANOS) return
        synchronized(this) {
            val recheckedNow = SystemClock.elapsedRealtimeNanos()
            if (recheckedNow - lastRefreshNanos >= REFRESH_INTERVAL_NANOS) {
                refreshCache()
            }
        }
    }

    @Synchronized
    private fun refreshCache(prefs: SharedPreferences? = preferences) {
        if (prefs == null) {
            cache = PreferencesSnapshot()
            lastRefreshNanos = SystemClock.elapsedRealtimeNanos()
            return
        }

        val mockProviderEnabled = prefs.getBoolean(
            KEY_ENABLE_MOCK_PROVIDER,
            DEFAULT_ENABLE_MOCK_PROVIDER
        )
        val point = parseLastClickedLocation(prefs.getString(KEY_LAST_CLICKED_LOCATION, null))
        cache = PreferencesSnapshot(
            // Mock Provider and Xposed replacement are mutually exclusive location sources.
            // Keeping installed interceptors inert also makes mode changes take effect without
            // restarting target apps that already have this module loaded.
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false) && !mockProviderEnabled && point != null,
            lastClickedLocation = point,
            useAccuracy = prefs.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY),
            accuracy = readDouble(prefs, KEY_ACCURACY, DEFAULT_ACCURACY),
            useAltitude = prefs.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE),
            altitude = readDouble(prefs, KEY_ALTITUDE, DEFAULT_ALTITUDE),
            useRandomize = prefs.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE),
            randomizeRadius = readDouble(prefs, KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS),
            useVerticalAccuracy = prefs.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY),
            verticalAccuracy = prefs.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY),
            useMeanSeaLevel = prefs.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL),
            meanSeaLevel = readDouble(prefs, KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL),
            useMeanSeaLevelAccuracy = prefs.getBoolean(
                KEY_USE_MEAN_SEA_LEVEL_ACCURACY,
                DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
            ),
            meanSeaLevelAccuracy = prefs.getFloat(
                KEY_MEAN_SEA_LEVEL_ACCURACY,
                DEFAULT_MEAN_SEA_LEVEL_ACCURACY
            ),
            useSpeed = prefs.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED),
            speed = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED),
            useSpeedAccuracy = prefs.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY),
            speedAccuracy = prefs.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY),
            hideFakeLocationToast = prefs.getBoolean(
                KEY_HIDE_FAKE_LOCATION_TOAST,
                DEFAULT_HIDE_FAKE_LOCATION_TOAST
            ),
            enableSystemHooks = prefs.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS),
            targetApps = parseTargetApps(prefs.getString(KEY_TARGET_APPS, null)),
            appCoordinateSystems = parseAppCoordinateSystems(
                prefs.getString(KEY_APP_COORDINATE_SYSTEMS, null)
            )
        )
        lastRefreshNanos = SystemClock.elapsedRealtimeNanos()
        val diagnostic = "hookActive=${cache.isPlaying}, mockProvider=$mockProviderEnabled, " +
            "hasPoint=${cache.lastClickedLocation != null}, systemHooks=${cache.enableSystemHooks}"
        if (diagnostic != lastDiagnostic) {
            lastDiagnostic = diagnostic
            log(diagnostic)
        }
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json.isNullOrBlank()) return null
        return runCatching { JsonCodec.decodeLocation(json) }
            .onFailure { log("Invalid saved location configuration", Log.ERROR) }
            .getOrNull()
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            JsonCodec.decodeStringSet(json)
        }.onFailure {
            log("Error parsing $KEY_TARGET_APPS JSON: ${it.message}", Log.ERROR)
        }.getOrDefault(emptySet())
    }

    private fun parseAppCoordinateSystems(json: String?): Map<String, CoordinateSystem> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            JsonCodec.decodeStringMap(json).mapValues { CoordinateSystem.fromStored(it.value) }
        }.onFailure {
            log("Error parsing $KEY_APP_COORDINATE_SYSTEMS JSON: ${it.message}", Log.ERROR)
        }.getOrDefault(emptyMap())
    }

    private fun readDouble(prefs: SharedPreferences, key: String, default: Double): Double {
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    data class PreferencesSnapshot(
        val isPlaying: Boolean = false,
        val lastClickedLocation: LastClickedLocation? = null,
        val useAccuracy: Boolean = DEFAULT_USE_ACCURACY,
        val accuracy: Double = DEFAULT_ACCURACY,
        val useAltitude: Boolean = DEFAULT_USE_ALTITUDE,
        val altitude: Double = DEFAULT_ALTITUDE,
        val useRandomize: Boolean = DEFAULT_USE_RANDOMIZE,
        val randomizeRadius: Double = DEFAULT_RANDOMIZE_RADIUS,
        val useVerticalAccuracy: Boolean = DEFAULT_USE_VERTICAL_ACCURACY,
        val verticalAccuracy: Float = DEFAULT_VERTICAL_ACCURACY,
        val useMeanSeaLevel: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL,
        val meanSeaLevel: Double = DEFAULT_MEAN_SEA_LEVEL,
        val useMeanSeaLevelAccuracy: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        val meanSeaLevelAccuracy: Float = DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        val useSpeed: Boolean = DEFAULT_USE_SPEED,
        val speed: Float = DEFAULT_SPEED,
        val useSpeedAccuracy: Boolean = DEFAULT_USE_SPEED_ACCURACY,
        val speedAccuracy: Float = DEFAULT_SPEED_ACCURACY,
        val hideFakeLocationToast: Boolean = DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        val enableSystemHooks: Boolean = DEFAULT_ENABLE_SYSTEM_HOOKS,
        val targetApps: Set<String> = emptySet(),
        val appCoordinateSystems: Map<String, CoordinateSystem> = emptyMap()
    )

    private const val REFRESH_INTERVAL_NANOS = 1_000_000_000L
}
