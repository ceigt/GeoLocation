package io.github.ceigt.geolocation.xposed.hooks

import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME

/** System controls, shared system UIDs and unresolved callers must retain real Wi-Fi state. */
internal fun isWifiLocationClient(uid: Int, packages: Set<String>): Boolean =
    uid >= 0 && uid % 100000 >= 10000 && packages.isNotEmpty() &&
        !usesRealLocationSwitch(packages) && MANAGER_APP_PACKAGE_NAME !in packages &&
        selectSystemTargetPackage(packages, emptySet()) != null
