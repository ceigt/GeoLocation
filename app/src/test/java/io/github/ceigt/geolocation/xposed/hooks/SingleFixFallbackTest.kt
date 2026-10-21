package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class SingleFixFallbackTest {
    @Test fun authorizedEmptyNativeResultCanBeReplaced() {
        assertEquals("fake", replaceSingleFix<String>(null, { true }, { "fake" }))
    }
    @Test fun deniedEmptyResultDoesNotCreateLocation() {
        assertNull(replaceSingleFix<String>(null, { false }, { error("Must not create a fix") }))
    }
    @Test fun nativeResultDoesNotRequireSupplementalAuthorization() {
        assertEquals("fake:original", replaceSingleFix("original", { error("Native already validated") }, { "fake:$it" }))
    }
}
