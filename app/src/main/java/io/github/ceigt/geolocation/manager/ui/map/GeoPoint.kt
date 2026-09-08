package io.github.ceigt.geolocation.manager.ui.map

/** Coordinates stored by GeoLocation are always WGS-84. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)
