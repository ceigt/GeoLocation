package io.github.ceigt.geolocation.xposed.hooks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import kotlin.math.cos
import kotlin.math.round

/** Permission-aware output at the final system boundary; never returns real data on adapter failure. */
internal object SystemLocationPrivacy {
    private val members = ReflectionCache()

    fun replace(service: Any, uid: Int, pid: Int, provider: String, original: Location?,
        packageName: String, failure: (Throwable) -> Unit): Location? = runCatching {
        val context = field(service, "mContext") as Context
        val coarse = hasCoarse(context, uid, pid)
        val fine = hasFine(context, uid, pid)
        if (!coarse && !fine) return@runCatching null
        val fake = LocationUtil.createFakeLocation(original, provider, targetPackage = packageName)
        if (fine) return@runCatching fake
        // Use this ROM's existing per-provider coarse filter, including its privacy offsets,
        // configured accuracy and optional-field removal. Do not invent a second coarse algorithm.
        coarseLocation(service, provider, fake, packageName, failure)
    }.onFailure(failure).getOrNull()

    internal fun coarseLocation(service: Any, provider: String, fake: Location,
        packageName: String, failure: (Throwable) -> Unit): Location {
        val result = runCatching {
            val lookup = method(service, "getLocationProviderManager", String::class.java)
            val manager = lookup.invoke(service, provider) ?: return@runCatching null
            val fudger = field(manager, "mLocationFudger") ?: return@runCatching null
            method(fudger, "createCoarse", Location::class.java).invoke(fudger, fake) as? Location
        }.onFailure(failure).getOrNull()
        return result?.let(::Location) ?: coarseFallback(fake, packageName)
    }

    fun hasFine(context: Context, uid: Int, pid: Int): Boolean =
        context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, pid, uid) == PackageManager.PERMISSION_GRANTED

    fun hasCoarse(context: Context, uid: Int, pid: Int): Boolean =
        context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, pid, uid) == PackageManager.PERMISSION_GRANTED

    /** Privacy-preserving fallback for ROMs that moved or removed LocationFudger internals. */
    internal fun coarseFallback(source: Location, packageName: String): Location = Location(source).apply {
        val latitudeStep = COARSE_METERS / METERS_PER_LATITUDE_DEGREE
        val longitudeStep = COARSE_METERS /
            (METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(latitude)).coerceAtLeast(0.1))
        // A package-stable phase avoids placing every coarse client on the same global grid edge.
        val phase = ((packageName.hashCode().toLong() and 0xffffL).toDouble() / 0xffffL) - 0.5
        latitude = round((latitude + phase * latitudeStep) / latitudeStep) * latitudeStep
        longitude = round((longitude - phase * longitudeStep) / longitudeStep) * longitudeStep
        latitude = latitude.coerceIn(-90.0, 90.0)
        longitude = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        accuracy = maxOf(accuracy, COARSE_METERS.toFloat())
        removeAltitude()
        removeSpeed()
        removeBearing()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            removeVerticalAccuracy()
            removeSpeedAccuracy()
            removeBearingAccuracy()
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            removeMslAltitude()
            removeMslAltitudeAccuracy()
        }
    }

    private fun field(owner: Any, name: String): Any? =
        (members.field(owner.javaClass, name) ?: error("Missing privacy field: $name")).get(owner)

    private fun method(owner: Any, name: String, vararg parameters: Class<*>): java.lang.reflect.Method =
        members.method(owner.javaClass, name, *parameters) ?: error("Missing privacy method: $name")

    private const val COARSE_METERS = 2_000.0
    private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
}
