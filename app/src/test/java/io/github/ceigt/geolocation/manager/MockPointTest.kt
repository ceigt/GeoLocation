package io.github.ceigt.geolocation.manager

import io.github.ceigt.geolocation.manager.mock.randomPoint
import io.github.ceigt.geolocation.manager.ui.map.CoordinateTransform
import org.junit.Assert.*
import org.junit.Test

class MockPointTest {
    @Test fun zeroRadiusPreservesPoint() { assertEquals(31.2 to 121.5, randomPoint(31.2, 121.5, 0.0, .5, .5)) }
    @Test fun polarAndDateLinePointsStayValid() {
        for (lat in listOf(-90.0, 0.0, 90.0)) {
            val (x, y) = randomPoint(lat, 179.9999, 1000.0, 1.0, .25)
            assertTrue(x.isFinite() && x in -90.0..90.0)
            assertTrue(y.isFinite() && y in -180.0..180.0)
        }
    }
    @Test fun bd09BoundaryReturnsGcj02Coordinates() {
        val bd = CoordinateTransform.gcj02ToBd09(31.2, 121.5)
        val gcj = CoordinateTransform.bd09ToGcj02(bd.latitude, bd.longitude)
        assertEquals(31.2, gcj.latitude, 0.000002)
        assertEquals(121.5, gcj.longitude, 0.000002)
    }
}
