package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test

class LocationPayloadCopyTest {
    @Test fun parcelCloneDoesNotShareNestedLocationWithProviderCache() {
        val original = Location("gps").apply { latitude = 31.0; longitude = 121.0; accuracy = 5f }
        val container = Bundle().apply { putParcelable("location", original) }
        val copy = copyLocationPayload(container) as Bundle
        @Suppress("DEPRECATION") val nested = copy.getParcelable<Location>("location")!!
        nested.latitude = 45.0
        assertEquals(31.0, original.latitude, 0.0)
        assertNotSame(original, nested)
    }

    @Test fun coarseFallbackKeepsValidCoordinatesAtPolesAndDateline() {
        for (lat in listOf(-90.0, 90.0)) for (lon in listOf(-180.0, 180.0)) {
            val source = Location("gps").apply { latitude = lat; longitude = lon; accuracy = 5f; altitude = 20.0; speed = 1f }
            val coarse = SystemLocationPrivacy.coarseFallback(source, "test.client")
            assertTrue(coarse.latitude in -90.0..90.0)
            assertTrue(coarse.longitude in -180.0..180.0)
            assertTrue(coarse.accuracy >= 2000f)
            assertFalse(coarse.hasAltitude())
            assertFalse(coarse.hasSpeed())
            assertEquals(5f, source.accuracy, 0f)
        }
    }
}
