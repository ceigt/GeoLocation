package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTargetSelectionTest {
    @Test fun systemOnlyScopeStillSelectsThirdPartyCaller() {
        val result = selectSystemTargetPackage(
            linkedSetOf("android", "com.android.phone", "com.eg.android.AlipayGphone"),
            setOf("system", "android", "com.android.phone")
        )
        assertEquals("com.eg.android.AlipayGphone", result)
    }

    @Test fun explicitThirdPartyTargetWinsForCoordinateSelection() {
        val result = selectSystemTargetPackage(
            linkedSetOf("com.google.android.gms", "com.tencent.wework"),
            setOf("system", "com.tencent.wework")
        )
        assertEquals("com.tencent.wework", result)
    }

    @Test fun frameworkOnlyDeliveryIsNotModified() {
        assertNull(selectSystemTargetPackage(
            setOf("android", "com.android.location.fused", "com.android.phone"),
            setOf("system", "android", "com.android.phone")
        ))
    }
}
