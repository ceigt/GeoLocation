package io.github.ceigt.geolocation.xposed.hooks

import android.app.ActivityManager
import android.content.Context

internal object ForegroundLocationAccess {
    private val members = ReflectionCache()

    /** System-server only: Android's capability distinguishes location FGS from music FGS. */
    fun systemCapability(context: Context, uid: Int): Boolean? = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return@runCatching null
        val method = members.method(manager.javaClass, "getUidProcessCapabilities", Int::class.javaPrimitiveType!!)
            ?: return@runCatching null
        val capabilities = method.invoke(manager, uid) as Int
        capabilities and 1 != 0 // PROCESS_CAPABILITY_FOREGROUND_LOCATION
    }.getOrNull()

    fun applicationForeground(visible: Boolean, serviceImportance: Boolean, rawMode: Int, evaluatedMode: Int): Boolean =
        visible || (serviceImportance && rawMode == 4 && evaluatedMode == 0)
}
