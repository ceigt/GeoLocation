package io.github.ceigt.geolocation.xposed.utils

private const val WECOM_PACKAGE = "com.tencent.wework"
private const val STATIONARY_COMPATIBILITY_SPEED_MPS = 0.001f

/** Preserve a stationary value while avoiding WeCom's exact `speed == 0` rejection path. */
internal fun compatibleSpeed(packageName: String?, speed: Float): Float =
    if (packageName == WECOM_PACKAGE && speed == 0f) STATIONARY_COMPATIBILITY_SPEED_MPS else speed
