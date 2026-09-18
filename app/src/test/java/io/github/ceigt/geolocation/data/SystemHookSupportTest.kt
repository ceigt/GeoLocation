package io.github.ceigt.geolocation.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHookSupportTest {
    @Test fun onlyVerifiedAndroidVersionsAreAccepted() {
        assertFalse(SystemHookSupport.supports(30))
        assertTrue(SystemHookSupport.supports(31))
        assertTrue(SystemHookSupport.supports(36))
        assertFalse(SystemHookSupport.supports(37))
    }
}
