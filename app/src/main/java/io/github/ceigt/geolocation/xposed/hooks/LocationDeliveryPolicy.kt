package io.github.ceigt.geolocation.xposed.hooks

/** Shared native/supplement admission. Rejected fixes never advance distance or count. */
internal class LocationDeliveryPolicy<T>(
    private val budget: SystemDeliveryBudget,
    private val minimumDistance: Float,
    private val distance: (T, T) -> Float
) {
    private var lastDelivered: T? = null
    @Synchronized fun accept(now: Long, candidates: List<T>, simulated: Boolean): List<T> {
        var previous = lastDelivered
        val eligible = candidates.filter { fix ->
            val prior = previous
            val moved = !simulated || minimumDistance <= 0f || prior == null || distance(prior, fix) >= minimumDistance
            if (moved) previous = fix
            moved
        }
        val count = budget.acquire(now, eligible.size, simulated)
        return eligible.take(count).also { accepted ->
            if (accepted.isNotEmpty()) lastDelivered = accepted.last()
        }
    }
}
