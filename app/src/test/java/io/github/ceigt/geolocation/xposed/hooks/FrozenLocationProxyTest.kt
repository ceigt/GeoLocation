package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class FrozenLocationProxyTest {
    interface Fix {
        fun getLatitude(): Double
        fun getLongitude(): Double
        fun getTime(): Long
        fun getAccuracy(): Float
        fun isMockGps(): Boolean
        fun getAddress(): String
    }
    class MutableFix : Fix {
        var lat = 1.0
        var label = "first"
        override fun getLatitude() = lat
        override fun getLongitude() = 2.0
        override fun getTime() = 10L
        override fun getAccuracy() = 3f
        override fun isMockGps() = true
        override fun getAddress() = label
    }

    @Test fun deliveredObjectKeepsCoordinatesTimeAndOriginalMetadata() {
        val original = MutableFix()
        val settings = mutableMapOf<String, Any?>("getLatitude" to 31.0, "getLongitude" to 121.0, "getTime" to 100L)
        val fix = frozenLocationProxy(Fix::class.java, original, settings) as Fix
        settings["getLatitude"] = 45.0
        original.lat = 12.0
        original.label = "later"
        assertEquals(31.0, fix.getLatitude(), 0.0)
        assertEquals(121.0, fix.getLongitude(), 0.0)
        assertEquals(100L, fix.getTime())
        assertEquals("first", fix.getAddress())
    }

    @Test fun sdkPrimitiveVariantsReceiveCompatibleValues() {
        val fix = frozenLocationProxy(Fix::class.java, MutableFix(), mapOf("getAccuracy" to 5.0, "isMockGps" to 0)) as Fix
        assertEquals(5f, fix.getAccuracy(), 0f)
        assertFalse(fix.isMockGps())
        assertTrue(fix == fix)
        assertFalse(fix == MutableFix())
    }
}
