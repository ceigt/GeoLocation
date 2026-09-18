package io.github.ceigt.geolocation.xposed.utils

internal object CompatibilityProfiles {
    const val WECOM_PACKAGE = "com.tencent.wework"
    const val WECOM_INITIAL_FIX_DELAY_MS = 20L
    const val STATIONARY_COMPATIBILITY_SPEED_MPS = 0.001f

    fun initialFixDelay(packageName: String, provider: String): Long =
        if (packageName == WECOM_PACKAGE && provider in setOf("gps", "passive")) {
            WECOM_INITIAL_FIX_DELAY_MS
        } else {
            0L
        }
}

/** Preserve a stationary value while avoiding WeCom's exact `speed == 0` rejection path. */
internal fun compatibleSpeed(packageName: String?, speed: Float): Float =
    if (packageName == CompatibilityProfiles.WECOM_PACKAGE && speed == 0f) {
        CompatibilityProfiles.STATIONARY_COMPATIBILITY_SPEED_MPS
    } else {
        speed
    }
