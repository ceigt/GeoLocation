package io.github.ceigt.geolocation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScopePolicyTest {
    @Test
    fun `system packages alone contain no application hook target`() {
        assertEquals(emptySet<String>(), applicationHookTargets(SYSTEM_HOOK_PACKAGES))
    }

    @Test
    fun `manager and system packages are excluded from application targets`() {
        assertEquals(
            setOf("com.tencent.mm", "com.tencent.wework"),
            applicationHookTargets(
                SYSTEM_HOOK_PACKAGES + MANAGER_APP_PACKAGE_NAME +
                    listOf("com.tencent.mm", "com.tencent.wework")
            )
        )
    }
}
