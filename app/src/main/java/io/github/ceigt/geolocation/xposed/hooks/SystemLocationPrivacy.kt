package io.github.ceigt.geolocation.xposed.hooks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import io.github.ceigt.geolocation.xposed.utils.LocationUtil

/** Permission-aware output at the final system boundary; never returns real data on adapter failure. */
internal object SystemLocationPrivacy {
    fun replace(service: Any, uid: Int, pid: Int, provider: String, original: Location,
        packageName: String, failure: (Throwable) -> Unit): Location? = runCatching {
        val context = field(service, "mContext") as Context
        val coarse = context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, pid, uid) == PackageManager.PERMISSION_GRANTED
        val fine = context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, pid, uid) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !fine) return@runCatching null
        val fake = LocationUtil.createFakeLocation(original, targetPackage = packageName)
        if (fine) return@runCatching fake
        // Use this ROM's existing per-provider coarse filter, including its privacy offsets,
        // configured accuracy and optional-field removal. Do not invent a second coarse algorithm.
        val lookup = method(service, "getLocationProviderManager", String::class.java)
        val manager = lookup.invoke(service, provider) ?: return@runCatching null
        val fudger = field(manager, "mLocationFudger") ?: return@runCatching null
        val result = method(fudger, "createCoarse", Location::class.java).invoke(fudger, fake) as? Location
        result?.let(::Location)
    }.onFailure(failure).getOrNull()

    private fun field(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(owner) }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing privacy field: $name")
    }
    private fun method(owner: Any, name: String, vararg parameters: Class<*>): java.lang.reflect.Method {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredMethod(name, *parameters).apply { isAccessible = true } }
            catch (_: NoSuchMethodException) { type = type.superclass }
        }
        error("Missing privacy method: $name")
    }
}
