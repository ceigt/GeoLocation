package io.github.ceigt.geomimic.manager.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformTest {
    @Test
    fun `wgs84 converts to gcj02 and bd09 near Beijing`() {
        val wgs = GeoPoint(latitude = 39.908823, longitude = 116.397470)
        val gcj = CoordinateTransform.wgs84ToGcj02(wgs.latitude, wgs.longitude)
        val bd = CoordinateTransform.wgs84ToBd09(wgs)

        assertEquals(39.9102, gcj.latitude, 0.001)
        assertEquals(116.4037, gcj.longitude, 0.001)
        assertEquals(39.9166, bd.latitude, 0.001)
        assertEquals(116.4101, bd.longitude, 0.001)
    }

    @Test
    fun `gcj02 conversion leaves locations outside China unchanged`() {
        val london = GeoPoint(latitude = 51.5074, longitude = -0.1278)
        val result = CoordinateTransform.wgs84ToGcj02(london.latitude, london.longitude)

        assertEquals(london.latitude, result.latitude, 0.0)
        assertEquals(london.longitude, result.longitude, 0.0)
    }
}
