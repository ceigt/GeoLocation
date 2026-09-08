package io.github.ceigt.geolocation.data.model

data class FavoriteLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)
