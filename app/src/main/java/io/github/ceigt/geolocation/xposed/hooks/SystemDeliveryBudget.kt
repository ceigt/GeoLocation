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
    @Synchronized fun remainingMillis(now: Long): Long = when {
        delivered >= maxUpdates -> 0L
        durationMillis == Long.MAX_VALUE -> Long.MAX_VALUE
        else -> (durationMillis - (now - started)).coerceAtLeast(0L)
    }
    @Synchronized fun expired(now: Long): Boolean = delivered >= maxUpdates || now - started >= durationMillis
    @Synchronized fun due(now: Long): Boolean = !expired(now) && (last == null || now - last!! >= interval)
    @Synchronized fun sent(now: Long) { last = now; if (delivered < Int.MAX_VALUE) delivered++ }
    /** Reserve once at the outermost delivery boundary; native batches share this allowance. */
    @Synchronized fun acquire(now: Long, count: Int, respectInterval: Boolean): Int {
        if (count <= 0 || expired(now) || (respectInterval && !due(now))) return 0
        val accepted = minOf(count, maxUpdates - delivered)
        last = now
        delivered += accepted
        return accepted
    }
}
