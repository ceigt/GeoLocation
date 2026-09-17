package io.github.ceigt.geolocation.xposed.hooks

import io.github.ceigt.geolocation.data.MANAGER_APP_PACKAGE_NAME
import org.junit.Assert.*
import org.junit.Test

class WifiCallerPolicyTest {
    @Test fun systemUiWithAppUidRetainsRealState() {
        assertFalse(isWifiLocationClient(10620, setOf("com.android.systemui")))
        assertFalse(isWifiLocationClient(10620, setOf("vendor.shared", "com.android.systemui")))
        assertFalse(isWifiLocationClient(1010620, setOf("com.android.settings")))
    }
    @Test fun secondaryUserSystemUidIsNotAnApplication() {
        assertFalse(isWifiLocationClient(1001000, setOf("vendor.system")))
        assertFalse(isWifiLocationClient(1000, setOf("vendor.system")))
    }
    @Test fun unknownAndManagerCallersRetainRealState() {
        assertFalse(isWifiLocationClient(10620, emptySet()))
        assertFalse(isWifiLocationClient(10620, setOf(MANAGER_APP_PACKAGE_NAME)))
    }
    @Test fun locationClientsRemainEligible() {
        assertTrue(isWifiLocationClient(10621, setOf("com.tencent.wework")))
        assertTrue(isWifiLocationClient(1010621, setOf("com.tencent.mm")))
    }
}
