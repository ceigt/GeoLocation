package io.github.ceigt.geolocation.xposed.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCompatibilityTest {
    @Test fun weComZeroSpeedRemainsEffectivelyStationaryButNonZero() {
        val result = compatibleSpeed("com.tencent.wework", 0f)
        assertTrue(result > 0f)
        assertTrue(result < 0.01f)
    }

    @Test fun configuredAndOtherAppSpeedsAreUnchanged() {
        assertEquals(1.5f, compatibleSpeed("com.tencent.wework", 1.5f))
        assertEquals(0f, compatibleSpeed("com.tencent.mm", 0f))
    }
}
