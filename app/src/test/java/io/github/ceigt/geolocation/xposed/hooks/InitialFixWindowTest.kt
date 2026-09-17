package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialFixWindowTest {
    @Test fun weComLocationWaitsBriefly() {
        val window = InitialFixWindow("com.tencent.wework", "gps", 1_000L)
        assertEquals(20L, window.remaining(1_000L))
        assertEquals(1L, window.remaining(1_019L))
        assertEquals(0L, window.remaining(1_020L))
    }

    @Test fun otherClientsAndGnssRemainImmediate() {
        assertEquals(0L, InitialFixWindow("com.tencent.mm", "gps", 1_000L).remaining(1_000L))
        assertEquals(0L, InitialFixWindow("com.tencent.wework", "_gnss", 1_000L).remaining(1_000L))
    }
}
