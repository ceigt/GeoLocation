package io.github.ceigt.geolocation.xposed.hooks

import java.util.IdentityHashMap

/** Identity-based registrations; queued deliveries must check that their entry is still current. */
internal class ListenerRegistry<K : Any, V : Any> {
    private val entries = IdentityHashMap<K, V>()
    @Synchronized fun put(key: K, value: V) { entries[key] = value }
    @Synchronized fun get(key: K): V? = entries[key]
    @Synchronized fun remove(key: K): V? = entries.remove(key)
    @Synchronized fun isCurrent(key: K, value: V): Boolean = entries[key] === value
    @Synchronized fun snapshot(): List<Pair<K, V>> = entries.entries.map { it.key to it.value }
}
