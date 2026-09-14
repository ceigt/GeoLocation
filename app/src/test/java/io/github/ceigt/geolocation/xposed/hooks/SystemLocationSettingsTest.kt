package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemLocationSettingsTest {
    @Test fun secureLocationModeIsSynthesizedAsEnabled() {
        assertEquals("3", syntheticSecureLocationSetting("GET_secure", "location_mode"))
        assertEquals("gps,network", syntheticSecureLocationSetting("GET_secure", "location_providers_allowed"))
    }

    @Test fun unrelatedSettingsAndWriteCallsAreUntouched() {
        assertNull(syntheticSecureLocationSetting("GET_secure", "android_id"))
        assertNull(syntheticSecureLocationSetting("PUT_secure", "location_mode"))
        assertNull(syntheticSecureLocationSetting(null, "location_mode"))
    }
}
