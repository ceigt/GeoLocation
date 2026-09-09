package io.github.ceigt.geolocation.xposed.hooks

/** Monotonic timing and request limits for supplemental system callbacks. */
internal class SystemDeliveryBudget(
    private val started: Long,
    intervalMillis: Long,
    private val durationMillis: Long,
    private val maxUpdates: Int
) {
    private val interval = intervalMillis.coerceAtLeast(1000L)
    private var last: Long? = null
    private var delivered = 0
    fun expired(now: Long): Boolean = delivered >= maxUpdates || now - started >= durationMillis
    fun due(now: Long): Boolean = !expired(now) && (last == null || now - last!! >= interval)
    fun sent(now: Long) { last = now; delivered++ }
}
