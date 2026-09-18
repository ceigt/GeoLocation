package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLocationPrivacyTest {
    class NullManagerService {
        @Suppress("UNUSED_PARAMETER") fun getLocationProviderManager(provider: String): Any? = null
    }

    @Test fun missingRomLookupAndMissingManagerBothUseCoarseFallback() {
        val source = Location("gps").apply {
            latitude = 31.2304
            longitude = 121.4737
            accuracy = 5f
            altitude = 50.0
            speed = 2f
            bearing = 90f
        }
        for (service in listOf(Any(), NullManagerService())) {
            val failures = mutableListOf<Throwable>()
            val result = SystemLocationPrivacy.coarseLocation(service, "gps", source,
                "test.client", failures::add)
            assertTrue(result.accuracy >= 2000f)
            assertFalse(result.hasAltitude())
            assertFalse(result.hasSpeed())
            assertFalse(result.hasBearing())
            assertEquals(5f, source.accuracy, 0f)
            if (service !is NullManagerService) assertEquals(1, failures.size)
        }
    }
}
