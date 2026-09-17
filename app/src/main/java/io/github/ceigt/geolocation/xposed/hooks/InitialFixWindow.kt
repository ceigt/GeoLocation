package io.github.ceigt.geolocation.xposed.hooks

/**
 * WeCom registers its location listener immediately before its GNSS listener and can reject a
 * stationary fix that reaches its SDK before satellite state. Keep this window shorter than the
 * shortest request lifetime observed on-device (about 60 ms); every other client remains immediate.
 */
internal class InitialFixWindow(
    packageName: String,
    provider: String,
    private val registeredAt: Long
) {
    private val delayMillis = if (
        packageName == "com.tencent.wework" && provider in setOf("gps", "passive")
    ) 20L else 0L

    fun remaining(now: Long): Long = (delayMillis - (now - registeredAt)).coerceAtLeast(0L)
}
