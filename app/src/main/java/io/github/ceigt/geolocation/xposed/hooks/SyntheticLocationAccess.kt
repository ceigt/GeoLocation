package io.github.ceigt.geolocation.xposed.hooks

/** Stored authorization for synthetic data; the real-location switch is handled separately. */
internal object SyntheticLocationAccess {
    fun allowed(uidMode: Int?, packageMode: Int?, defaultMode: Int, foreground: Boolean): Boolean {
        val mode = uidMode?.takeIf { it != defaultMode } ?: packageMode ?: defaultMode
        return mode == 0 || (mode == 4 && foreground)
    }
}
