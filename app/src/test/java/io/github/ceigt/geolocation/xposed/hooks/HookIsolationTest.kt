package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class HookIsolationTest {
    @Test fun unsupportedAdapterDoesNotPreventNextAdapter() {
        val failure = NoSuchMethodException("missing on this ROM")
        var reported: Throwable? = null
        var installed = false
        isolateHook({ reported = it }) { throw failure }
        isolateHook({ fail("working adapter should not fail") }) { installed = true }
        assertSame(failure, reported)
        assertTrue(installed)
    }

    @Test fun incompatibleSdkIsReported() {
        val failure = NoClassDefFoundError("optional SDK")
        var reported: Throwable? = null
        isolateHook({ reported = it }) { throw failure }
        assertSame(failure, reported)
    }

    @Test fun fatalVmFailureIsNotHidden() {
        val failure = OutOfMemoryError("test sentinel")
        try {
            isolateHook({ fail("VM failure must propagate") }) { throw failure }
            fail("expected VM failure")
        } catch (actual: OutOfMemoryError) {
            assertSame(failure, actual)
        }
    }
}
