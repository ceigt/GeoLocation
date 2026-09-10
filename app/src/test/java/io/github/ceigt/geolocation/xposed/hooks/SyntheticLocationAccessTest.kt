package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class SyntheticLocationAccessTest {
    @Test fun foregroundAuthorizationNeverAllowsBackground() {
        assertTrue(SyntheticLocationAccess.allowed(4, 0, 0, true))
        assertFalse(SyntheticLocationAccess.allowed(4, 0, 0, false))
    }
    @Test fun explicitDenialsRemainDenied() {
        for (denied in listOf(1, 2, 3)) {
            assertFalse(SyntheticLocationAccess.allowed(denied, 0, 0, true))
            assertFalse(SyntheticLocationAccess.allowed(null, denied, 0, true))
        }
    }
    @Test fun defaultUidModeFallsThroughToPackagePolicy() {
        assertFalse(SyntheticLocationAccess.allowed(0, 1, 0, true))
        assertTrue(SyntheticLocationAccess.allowed(null, 0, 0, true))
        assertFalse(SyntheticLocationAccess.allowed(null, null, 3, true))
    }
}
