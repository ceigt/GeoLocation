package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class ReflectionCacheTest {
    private open class Parent { private val value = 7; private fun number(x: Int) = x + 1 }
    private class Child : Parent()
    @Test fun inheritedPrivateMembersAndMisses() {
        val cache = ReflectionCache()
        val type = Child::class.java
        assertEquals(7, cache.field(type, "value")!!.get(Child()))
        assertSame(cache.field(type, "value"), cache.field(type, "value"))
        assertEquals(4, cache.method(type, "number", Int::class.javaPrimitiveType!!)!!.invoke(Child(), 3))
        repeat(2) { assertNull(cache.method(type, "number", String::class.java)); assertNull(cache.field(type, "missing")) }
    }
}
