package io.github.ceigt.geolocation.data

/** Coordinate system delivered to a selected target app. Stored map points remain WGS-84. */
enum class CoordinateSystem {
    WGS84,
    GCJ02,
    BD09;

    fun next(): CoordinateSystem = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStored(value: String?): CoordinateSystem =
            entries.firstOrNull { it.name == value } ?: WGS84
    }
}
