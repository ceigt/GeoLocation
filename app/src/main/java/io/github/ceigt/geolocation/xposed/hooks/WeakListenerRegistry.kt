package io.github.ceigt.geolocation.xposed.hooks

import java.lang.ref.WeakReference

/** The SDK owns live proxies. This lookup must not keep a completed SDK request alive. */
internal class WeakListenerRegistry {
    private class Entry(owner: Any, listener: Any, proxy: Any, val single: Boolean) {
        val owner = WeakReference(owner)
        val listener = WeakReference(listener)
        val proxy = WeakReference(proxy)
        var pending = 0
        var active = false
    }
    private val entries = mutableListOf<Entry>()
    private fun prune() { entries.removeAll { it.owner.get() == null || it.listener.get() == null || it.proxy.get() == null } }
    @Synchronized fun acquire(owner: Any, listener: Any, single: Boolean, factory: () -> Any): Any {
        prune()
        val entry = if (single) null else entries.firstOrNull {
            !it.single && it.owner.get() === owner && it.listener.get() === listener
        }
        val existing = entry?.proxy?.get()
        if (existing != null) { entry.pending++; return existing }
        val proxy = factory()
        entries.add(Entry(owner, listener, proxy, single).apply { pending = 1 })
        return proxy
    }
    @Synchronized fun registered(proxy: Any, success: Boolean) {
        val entry = entries.firstOrNull { it.proxy.get() === proxy } ?: return
        entry.pending = (entry.pending - 1).coerceAtLeast(0)
        entry.active = entry.active || success
        if (!entry.active && entry.pending == 0) entries.remove(entry)
    }
    @Synchronized fun proxies(owner: Any, listener: Any): List<Any> {
        prune()
        return entries.filter { it.owner.get() === owner && it.listener.get() === listener }.mapNotNull { it.proxy.get() }
    }
    @Synchronized fun remove(proxy: Any) { entries.removeAll { it.proxy.get() == null || it.proxy.get() === proxy } }
}
