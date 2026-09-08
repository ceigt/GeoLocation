package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class ListenerRegistryTest {
    private class EqualListener {
        override fun equals(other: Any?) = other is EqualListener
        override fun hashCode() = 1
    }

    @Test fun equalListenersAreIndependentAndRemovalCancelsQueuedDelivery() {
        val registry = ListenerRegistry<EqualListener, Any>()
        val first = EqualListener()
        val second = EqualListener()
        val a = Any()
        val b = Any()
        registry.put(first, a)
        registry.put(second, b)
        val queued = registry.snapshot()
        registry.remove(first)
        assertEquals(2, queued.size)
        assertFalse(registry.isCurrent(first, a))
        assertTrue(registry.isCurrent(second, b))
    }

    @Test fun reregisterInvalidatesOldQueuedCallback() {
        val registry = ListenerRegistry<Any, Any>()
        val listener = Any()
        val old = Any()
        val replacement = Any()
        registry.put(listener, old)
        registry.put(listener, replacement)
        assertFalse(registry.isCurrent(listener, old))
        assertTrue(registry.isCurrent(listener, replacement))
        assertSame(replacement, registry.remove(listener))
        assertTrue(registry.snapshot().isEmpty())
    }
}
