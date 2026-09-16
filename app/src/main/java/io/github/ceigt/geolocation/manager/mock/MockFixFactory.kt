package io.github.ceigt.geolocation.manager.mock

import android.location.Location
import android.os.Build
import android.os.SystemClock
import io.github.ceigt.geolocation.data.*
import io.github.ceigt.geolocation.data.model.LastClickedLocation
import kotlin.math.*
import kotlin.random.Random

/** One immutable preference snapshot and one random point for all providers in a tick. */
internal object MockFixFactory {
    fun create(point: LastClickedLocation, accuracy: Float, values: Map<String, *>): Location {
        fun enabled(key: String) = values[key] == true
        fun double(key: String, fallback: Double) = (values[key] as? Long)?.let(Double::fromBits)
            ?.takeIf(Double::isFinite) ?: fallback
        fun float(key: String, fallback: Float) = (values[key] as? Float)?.takeIf(Float::isFinite) ?: fallback
        val radius = if (enabled(KEY_USE_RANDOMIZE)) double(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS).coerceAtLeast(0.0) else 0.0
        val offset = randomPoint(point.latitude, point.longitude, radius, Random.nextDouble(), Random.nextDouble())
        return Location("gps").apply {
            latitude = offset.first
            longitude = offset.second
            this.accuracy = accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (enabled(KEY_USE_ALTITUDE)) altitude = double(KEY_ALTITUDE, DEFAULT_ALTITUDE)
            if (enabled(KEY_USE_SPEED)) speed = float(KEY_SPEED, DEFAULT_SPEED).coerceAtLeast(0f)
            if (enabled(KEY_USE_VERTICAL_ACCURACY)) verticalAccuracyMeters = float(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY).coerceAtLeast(0f)
            if (enabled(KEY_USE_SPEED_ACCURACY)) speedAccuracyMetersPerSecond = float(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY).coerceAtLeast(0f)
            if (Build.VERSION.SDK_INT >= 34) {
                if (enabled(KEY_USE_MEAN_SEA_LEVEL)) mslAltitudeMeters = double(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL)
                if (enabled(KEY_USE_MEAN_SEA_LEVEL_ACCURACY)) mslAltitudeAccuracyMeters = float(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY).coerceAtLeast(0f)
            }
        }
    }
}

internal fun randomPoint(latitude: Double, longitude: Double, radius: Double, distanceSample: Double, angleSample: Double): Pair<Double, Double> {
    if (radius <= 0.0 || !radius.isFinite()) return latitude to longitude
    val distance = sqrt(distanceSample.coerceIn(0.0, 1.0)) * radius.coerceAtMost(20_000_000.0) / 6_371_000.0
    val angle = angleSample * 2 * kotlin.math.PI
    val lat = Math.toRadians(latitude)
    val lon = Math.toRadians(longitude)
    val nextLat = asin((sin(lat) * cos(distance) + cos(lat) * sin(distance) * cos(angle)).coerceIn(-1.0, 1.0))
    val nextLon = lon + atan2(sin(angle) * sin(distance) * cos(lat), cos(distance) - sin(lat) * sin(nextLat))
    return Math.toDegrees(nextLat) to ((Math.toDegrees(nextLon) + 540.0) % 360.0 - 180.0)
}
