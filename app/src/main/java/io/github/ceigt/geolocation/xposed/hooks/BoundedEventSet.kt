package io.github.ceigt.geolocation.xposed.hooks

/** Prevent process-lifetime diagnostic de-duplication sets from growing without a bound. */
internal fun <T> MutableSet<T>.addBounded(value: T, maximumSize: Int = 512): Boolean =
    synchronized(this) {
        if (size >= maximumSize) clear()
        add(value)
    }
