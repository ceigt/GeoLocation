package io.github.ceigt.geolocation.manager.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationModeTest {
    @Test
    fun `no compatibility provider selects application hook`() {
        assertEquals(
            LocationMode.APPLICATION_HOOK,
            resolveLocationMode(mockProviderEnabled = false, systemHooksEnabled = false)
        )
    }

    @Test
    fun `system hook is selected when enabled`() {
        assertEquals(
            LocationMode.SYSTEM_HOOK,
            resolveLocationMode(mockProviderEnabled = false, systemHooksEnabled = true)
        )
    }

    @Test
    fun `mock provider takes precedence`() {
        assertEquals(
            LocationMode.MOCK_PROVIDER,
            resolveLocationMode(mockProviderEnabled = true, systemHooksEnabled = true)
        )
    }
}
