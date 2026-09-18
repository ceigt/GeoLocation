package io.github.ceigt.geolocation.data

/** System-server adapters are verified only for Android 12 through Android 16. */
object SystemHookSupport {
    const val MIN_SDK = 31
    const val MAX_SDK = 36

    fun supports(sdk: Int): Boolean = sdk in MIN_SDK..MAX_SDK
}
