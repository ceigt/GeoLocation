package io.github.ceigt.geolocation.data

enum class MapProvider(val storedValue: String) {
    BAIDU("baidu"),
    AMAP("amap"),
    GOOGLE("google");

    companion object {
        fun fromStored(value: String?): MapProvider =
            entries.firstOrNull { it.storedValue == value } ?: BAIDU
    }
}
