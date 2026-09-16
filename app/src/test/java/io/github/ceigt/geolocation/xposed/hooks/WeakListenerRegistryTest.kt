package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class WeakListenerRegistryTest {
    @Test fun failedOverlappingRegistrationDoesNotDiscardSuccessfulMapping() {
        val registry = WeakListenerRegistry()
        val manager = Any(); val listener = Any()
        val first = registry.acquire(manager, listener, false) { Any() }
        val second = registry.acquire(manager, listener, false) { Any() }
        assertSame(first, second)
        registry.registered(first, false)
        registry.registered(second, true)
        assertSame(second, registry.proxies(manager, listener).singleOrNull())
    }
    private class EqualListener { override fun equals(other: Any?) = other is EqualListener; override fun hashCode() = 1 }
    @Test fun identityAndManagerIsolation() {
        val registry = WeakListenerRegistry()
        val manager = Any(); val listener = EqualListener(); val proxy = Any()
        registry.acquire(manager, listener, false) { proxy }
        assertSame(proxy, registry.proxies(manager, listener).singleOrNull())
        assertNull(registry.proxies(manager, EqualListener()).singleOrNull())
        assertNull(registry.proxies(Any(), listener).singleOrNull())
    }
    @Test fun finishingSinglePreservesContinuousRequest() {
        val registry = WeakListenerRegistry()
        val manager = Any(); val listener = Any(); val continuous = Any(); val single = Any()
        registry.acquire(manager, listener, false) { continuous }
        registry.acquire(manager, listener, true) { single }
        registry.remove(single)
        assertEquals(listOf(continuous), registry.proxies(manager, listener))
        registry.remove(continuous)
        assertTrue(registry.proxies(manager, listener).isEmpty())
    }
}
