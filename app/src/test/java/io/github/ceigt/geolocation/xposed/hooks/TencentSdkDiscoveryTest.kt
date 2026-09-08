package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class TencentSdkDiscoveryTest {
    @Test fun discoversSappWhenStandardNamespaceIsAbsent() {
        val loader = javaClass.classLoader!!
        val resolved = TencentSdkDiscovery.namespaces.mapNotNull {
            runCatching { TencentSdkDiscovery.resolve(it, loader) }.getOrNull()
        }
        assertEquals(1, resolved.size)
        val sdk = resolved.single()
        assertEquals("com.tencent.map.geolocation.sapp.TencentLocationManager", sdk.manager.name)
        assertTrue(sdk.manager.getMethod("requestLocationUpdates", Any::class.java, sdk.listener)
            .parameterTypes.contains(sdk.listener))
        assertSame(sdk.location, sdk.listener.getMethod("onLocationChanged", sdk.location,
            Int::class.javaPrimitiveType, String::class.java).parameterTypes[0])
    }

    @Test fun missingNamespaceDoesNotResolveUnrelatedClasses() {
        assertTrue(runCatching {
            TencentSdkDiscovery.resolve("unknown.sdk", javaClass.classLoader!!)
        }.exceptionOrNull() is ClassNotFoundException)
    }
}
