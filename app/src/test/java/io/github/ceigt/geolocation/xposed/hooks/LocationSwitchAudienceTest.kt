package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSwitchAudienceTest {
    @Test fun quickSettingsAndSettingsKeepTheRealSwitch() {
        assertTrue(usesRealLocationSwitch(setOf("com.android.systemui")))
        assertTrue(usesRealLocationSwitch(setOf("com.android.settings")))
    }

    @Test fun sharedUidCannotHideSystemUiBehindAnotherPackage() {
        assertTrue(usesRealLocationSwitch(linkedSetOf("vendor.shared", "com.android.systemui")))
    }

    @Test fun ordinaryLocationClientsRemainEligible() {
        assertFalse(usesRealLocationSwitch(setOf("com.tencent.wework")))
        assertFalse(usesRealLocationSwitch(setOf("com.tencent.mm")))
        assertFalse(usesRealLocationSwitch(setOf("com.google.android.gms")))
    }
}
