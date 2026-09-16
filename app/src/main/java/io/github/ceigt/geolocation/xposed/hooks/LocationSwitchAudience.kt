package io.github.ceigt.geolocation.xposed.hooks

/** UI controls must see the real switch even on ROMs giving SystemUI an app-range UID. */
internal fun usesRealLocationSwitch(packages: Set<String>): Boolean =
    packages.any { it == "com.android.systemui" || it == "com.android.settings" }
