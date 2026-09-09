// LocationUtil.kt
package io.github.ceigt.geolocation.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.data.DEFAULT_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_ALTITUDE
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL
import io.github.ceigt.geolocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_RANDOMIZE_RADIUS
import io.github.ceigt.geolocation.data.DEFAULT_SPEED
import io.github.ceigt.geolocation.data.DEFAULT_SPEED_ACCURACY
import io.github.ceigt.geolocation.data.DEFAULT_VERTICAL_ACCURACY
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.data.PI
import io.github.ceigt.geolocation.data.RADIUS_EARTH
import io.github.ceigt.geolocation.manager.ui.map.CoordinateTransform
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Random
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtil {
    private const val TAG = "[LocationUtil]"

    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null

    private fun log(message: String, priority: Int = Log.INFO) {
        logger?.invoke(priority, TAG, message)
    }

    private const val DEBUG: Boolean = false

    private val random: Random = Random()
    @Volatile private var canAttemptMockProviderHide: Boolean = true
    @Volatile private var lastAppliedConfig: PreferencesUtil.PreferencesSnapshot? = null
    @Volatile private var lastAppliedTargetPackage: String? = null
    @Volatile private var lastAppliedAtNanos: Long = Long.MIN_VALUE
    private var randomizedAtNanos: Long = Long.MIN_VALUE
    private var randomizedBaseLatitude: Double = Double.NaN
    private var randomizedBaseLongitude: Double = Double.NaN
    private var randomizedRadius: Double = Double.NaN
    private var randomizedPoint: Pair<Double, Double>? = null

    @Volatile var latitude: Double = 0.0
    @Volatile var longitude: Double = 0.0
    @Volatile var accuracy: Float = 0F
    @Volatile var altitude: Double = 0.0
    @Volatile var verticalAccuracy: Float = 0F
    @Volatile var meanSeaLevel: Double = 0.0
    @Volatile var meanSeaLevelAccuracy: Float = 0F
    @Volatile var speed: Float = 0F
    @Volatile var speedAccuracy: Float = 0F
    @Volatile var targetPackageName: String? = null

    @Synchronized
    fun createFakeLocation(
        originalLocation: Location? = null,
        provider: String = LocationManager.GPS_PROVIDER,
        targetPackage: String? = targetPackageName
    ): Location {
        val config = PreferencesUtil.snapshot()
        updateLocation(config, targetPackage)

        val nowMillis = System.currentTimeMillis()
        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val sourceProvider = originalLocation?.provider?.takeIf { it.isNotBlank() } ?: provider
        val fakeLocation = if (originalLocation == null) {
            Location(sourceProvider).apply {
                time = nowMillis
                elapsedRealtimeNanos = nowElapsedNanos
                // Location#isComplete requires accuracy. Several one-shot and vendor location
                // clients discard a newly constructed Location when this flag is absent.
                accuracy = FALLBACK_ACCURACY_METERS
            }
        } else {
            Location(sourceProvider).apply {
                // Emit a fresh, complete fix. Preserving a stale timestamp can make Tencent and
                // fused clients ignore an otherwise valid replacement.
                time = nowMillis
                accuracy = if (originalLocation.hasAccuracy() && originalLocation.accuracy.isFinite()) {
                    originalLocation.accuracy.coerceAtLeast(0F)
                } else FALLBACK_ACCURACY_METERS
                if (originalLocation.hasBearing()) bearing = originalLocation.bearing
                if (originalLocation.hasBearingAccuracy()) bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                elapsedRealtimeNanos = nowElapsedNanos
                if (originalLocation.hasVerticalAccuracy()) verticalAccuracyMeters = originalLocation.verticalAccuracyMeters
            }
        }

        fakeLocation.latitude = latitude
        fakeLocation.longitude = longitude

        if (config.useAccuracy) {
            fakeLocation.accuracy = accuracy
        }

        if (config.useAltitude) {
            fakeLocation.altitude = altitude
        }

        if (config.useVerticalAccuracy) {
            fakeLocation.verticalAccuracyMeters = verticalAccuracy
        }

        if (config.useSpeed) {
            fakeLocation.speed = speed
        }

        if (config.useSpeedAccuracy) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (config.useMeanSeaLevel) {
                fakeLocation.mslAltitudeMeters = meanSeaLevel
            }

            if (config.useMeanSeaLevelAccuracy) {
                fakeLocation.mslAltitudeAccuracyMeters = meanSeaLevelAccuracy
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    private fun attemptHideMockProvider(fakeLocation: Location) {
        if (!canAttemptMockProviderHide) return
        try {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            if (DEBUG) {
                log("invoked hidden API - setIsFromMockProvider: false)")
            }
        } catch (e: Exception) {
            // A missing/blocked hidden API will fail the same way for every Location instance.
            // Circuit-break after the first failure instead of paying exception cost per update.
            canAttemptMockProviderHide = false
            log("Not possible to hide mock provider - ${e.message}", priority = Log.ERROR)
        }
    }

    fun updateLocation(
        config: PreferencesUtil.PreferencesSnapshot = PreferencesUtil.snapshot(),
        targetPackage: String? = targetPackageName
    ) {
        val now = SystemClock.elapsedRealtimeNanos()
        val randomizationStillFresh = !config.useRandomize ||
            now - lastAppliedAtNanos < RANDOMIZATION_INTERVAL_NANOS
        if (config === lastAppliedConfig && targetPackage == lastAppliedTargetPackage && randomizationStillFresh) return

        synchronized(this) {
            val recheckedNow = SystemClock.elapsedRealtimeNanos()
            val recheckedRandomizationStillFresh = !config.useRandomize ||
                recheckedNow - lastAppliedAtNanos < RANDOMIZATION_INTERVAL_NANOS
            if (config === lastAppliedConfig && targetPackage == lastAppliedTargetPackage && recheckedRandomizationStillFresh) return

            try {
            config.lastClickedLocation?.let {
                val storedPoint = GeoPoint(it.latitude, it.longitude)
                val outputPoint = when (
                    config.appCoordinateSystems[targetPackage] ?: CoordinateSystem.WGS84
                ) {
                    CoordinateSystem.WGS84 -> storedPoint
                    CoordinateSystem.GCJ02 -> CoordinateTransform.wgs84ToGcj02(
                        storedPoint.latitude,
                        storedPoint.longitude
                    )
                    CoordinateSystem.BD09 -> CoordinateTransform.wgs84ToBd09(storedPoint)
                }
                if (config.useRandomize) {
                    val randomLocation = getStableRandomLocation(
                        outputPoint.latitude,
                        outputPoint.longitude,
                        config.randomizeRadius
                    )
                    latitude = randomLocation.first
                    longitude = randomLocation.second
                } else {
                    latitude = outputPoint.latitude
                    longitude = outputPoint.longitude
                }

                accuracy = if (config.useAccuracy) {
                    config.accuracy.toFloat()
                } else {
                    DEFAULT_ACCURACY.toFloat()
                }

                altitude = if (config.useAltitude) {
                    config.altitude
                } else {
                    DEFAULT_ALTITUDE
                }

                verticalAccuracy = if (config.useVerticalAccuracy) {
                    config.verticalAccuracy
                } else {
                    DEFAULT_VERTICAL_ACCURACY
                }

                meanSeaLevel = if (config.useMeanSeaLevel) {
                    config.meanSeaLevel
                } else {
                    DEFAULT_MEAN_SEA_LEVEL
                }

                meanSeaLevelAccuracy = if (config.useMeanSeaLevelAccuracy) {
                    config.meanSeaLevelAccuracy
                } else {
                    DEFAULT_MEAN_SEA_LEVEL_ACCURACY
                }

                speed = if (config.useSpeed) {
                    config.speed
                } else {
                    DEFAULT_SPEED
                }

                speedAccuracy = if (config.useSpeedAccuracy) {
                    config.speedAccuracy
                } else {
                    DEFAULT_SPEED_ACCURACY
                }

                if (DEBUG) {
                    log("Updated fake location values to:")
                    log("\tCoordinates: (latitude = $latitude, longitude = $longitude)")
                    log("\tAccuracy: $accuracy")
                    log("\tAltitude: $altitude")
                    log("\tVertical Accuracy: $verticalAccuracy")
                    log("\tMean Sea Level: $meanSeaLevel")
                    log("\tMean Sea Level Accuracy: $meanSeaLevelAccuracy")
                    log("\tSpeed: $speed")
                    log("\tSpeed Accuracy: $speedAccuracy")
                }
            } ?: run {
                if (DEBUG) {
                    log("Last clicked location is null")
                }
            }
                lastAppliedConfig = config
                lastAppliedTargetPackage = targetPackage
                lastAppliedAtNanos = recheckedNow
            } catch (e: Exception) {
                log("Error - ${e.message}", priority = Log.ERROR)
            }
        }
    }

    private fun getStableRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val now = SystemClock.elapsedRealtimeNanos()
        val cached = randomizedPoint
        if (cached != null &&
            lat == randomizedBaseLatitude &&
            lon == randomizedBaseLongitude &&
            radiusInMeters == randomizedRadius &&
            now - randomizedAtNanos < RANDOMIZATION_INTERVAL_NANOS
        ) {
            return cached
        }

        return getRandomLocation(lat, lon, radiusInMeters).also {
            randomizedBaseLatitude = lat
            randomizedBaseLongitude = lon
            randomizedRadius = radiusInMeters
            randomizedAtNanos = now
            randomizedPoint = it
        }
    }

    // Calculates a random point within a circle around the fake location that has the radius set by by the user. Uses Haversine's formula.
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        // Generate two random numbers
        val rand1 = random.nextDouble()
        val rand2 = random.nextDouble()

        // Random distance and bearing
        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        // Convert back to degrees
        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        // Normalize longitude to be between -180 and 180 degrees
        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        // Clamp latitude to -90 to 90 degrees
        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }

    private const val RANDOMIZATION_INTERVAL_NANOS = 1_000_000_000L
    private const val FALLBACK_ACCURACY_METERS = 5F
}
