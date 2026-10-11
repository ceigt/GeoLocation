package io.github.ceigt.geolocation.xposed.hooks

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Process
import io.github.ceigt.geolocation.xposed.utils.LocationUtil

/** Application supplements use current authorization, not only registration-time permission. */
internal object ApplicationLocationAccess {
    @Volatile private var context: Context? = null
    private val members = ReflectionCache()

    fun init(value: Context) { context = value.applicationContext }

    fun init(manager: LocationManager) {
        if (context == null) {
            runCatching { members.field(manager.javaClass, "mContext")?.get(manager) as? Context }
                .getOrNull()?.let(::init)
        }
    }

    fun coarseOnly(): Boolean = context?.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED

    fun maySupplement(): Boolean = runCatching {
        val app = context ?: return@runCatching false
        val fine = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return@runCatching false
        val importance = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance
        val foreground = importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        if (!foreground && app.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return@runCatching false
        val ops = app.getSystemService(AppOpsManager::class.java) ?: return@runCatching false
        val op = if (fine) AppOpsManager.OPSTR_FINE_LOCATION else AppOpsManager.OPSTR_COARSE_LOCATION
        val mode = ops.unsafeCheckOpRawNoThrow(op, Process.myUid(), app.opPackageName)
        SyntheticLocationAccess.allowed(mode.takeUnless { it == AppOpsManager.MODE_DEFAULT }, null, AppOpsManager.MODE_ALLOWED, foreground)
    }.getOrDefault(false)

    fun replace(original: Location, provider: String = original.provider.orEmpty()): Location? {
        val app = context ?: return null
        val fine = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val fake = LocationUtil.createFakeLocation(original, provider)
        return if (fine) fake else SystemLocationPrivacy.coarseFallback(fake, app.packageName)
    }
}
