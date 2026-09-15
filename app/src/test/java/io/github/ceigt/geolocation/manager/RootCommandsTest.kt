package io.github.ceigt.geolocation.manager

import io.github.ceigt.geolocation.manager.control.RootCommands
import org.junit.Assert.*
import org.junit.Test

class RootCommandsTest {
    @Test fun onlyPackageIdentifiersMayReachRootCommands() {
        for (value in listOf("com.tencent.mm", "com.tencent.wework", "io.github.ceigt.geolocation"))
            assertTrue(RootCommands.validPackage(value))
        for (value in listOf("system", "", "com.app;id", "com.app'", "com.app\n", "com.app test", "../com.app"))
            assertFalse(RootCommands.validPackage(value))
    }
}
