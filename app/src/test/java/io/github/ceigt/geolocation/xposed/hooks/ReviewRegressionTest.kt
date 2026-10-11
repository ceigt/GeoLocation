package io.github.ceigt.geolocation.xposed.hooks

import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ReviewRegressionTest {
    private val client = "test.client"

    @Test fun dottedAttributionCannotChangeVerifiedCaller() {
        assertEquals(client, selectVerifiedSystemTargetPackage(setOf(client), client, emptySet()))
        assertEquals(client, selectVerifiedSystemTargetPackage(setOf(client), "test.attribution", emptySet()))
        assertNull(selectVerifiedSystemTargetPackage(setOf("test.one", "test.two"), "test.attribution", emptySet()))
    }

    @Test fun sharedUidUsesOnlyItsValidatedPackage() {
        assertEquals("test.two", selectVerifiedSystemTargetPackage(setOf("test.one", "test.two"), "test.two", emptySet()))
        assertNull(selectVerifiedSystemTargetPackage(setOf("test.one", "test.two"), "unrelated.app", emptySet()))
    }

    private fun chain(native: () -> Any?): Chain = Proxy.newProxyInstance(Chain::class.java.classLoader,
        arrayOf(Chain::class.java)) { _, method, _ ->
        when (method.name) {
            "proceed", "proceedWith" -> native()
            "getArgs" -> emptyList<Any?>()
            else -> null
        }
    } as Chain

    @Test fun adapterFailureAfterNativeCallNeverReplaysNative() {
        var calls = 0
        val errors = mutableListOf<Throwable>()
        val hook = guardRuntimeHook(errors::add, { null }, Hooker { native ->
            native.proceed()
            throw NoSuchMethodException("vendor field")
        })
        assertNull(hook.intercept(chain { calls++; "native" }))
        assertEquals(1, calls)
        assertEquals(1, errors.size)
    }

    @Test fun nativePermissionExceptionStillReachesCaller() {
        val expected = SecurityException("native denial")
        val hook = guardRuntimeHook({ fail("native failure is not an adapter failure") }, { null }, Hooker { it.proceed() })
        try { hook.intercept(chain { throw expected }); fail("Expected native permission failure") }
        catch (actual: SecurityException) { assertSame(expected, actual) }
    }

    @Test fun pausedModePreservesCompletedNativeResultWithoutReplay() {
        var calls = 0
        val hook = guardRuntimeHook({}, { null }, Hooker { native ->
            native.proceed()
            throw NoSuchMethodException("adapter")
        }, { true })
        assertEquals("original", hook.intercept(chain { calls++; "original" }))
        assertEquals(1, calls)
    }

    @Test fun runtimeGuardDoesNotHideVmFailure() {
        val expected = OutOfMemoryError("sentinel")
        val hook = guardRuntimeHook({ fail("must propagate") }, { null }, Hooker { throw expected })
        try { hook.intercept(chain { null }); fail("Expected VM failure") }
        catch (actual: OutOfMemoryError) { assertSame(expected, actual) }
    }

    @Test fun nativeSubsecondCallbacksRetainFrequencyAndFiniteBudget() {
        val budget = SystemDeliveryBudget(0, 200, 10000, 5)
        val policy = LocationDeliveryPolicy<Int>(budget, 0f) { a, b -> (a-b).toFloat() }
        for (index in 0 until 5) assertEquals(listOf(index), policy.accept(index * 200L, listOf(index), true, false))
        assertTrue(policy.accept(1000, listOf(5), true, false).isEmpty())
        assertTrue(policy.accept(2000, listOf(6), true).isEmpty())
    }

    @Test fun appBudgetCanHonorSubsecondRequestInterval() {
        val budget = SystemDeliveryBudget(0, 200, 10000, 3, 0)
        assertEquals(1, budget.acquire(0, 1, true))
        assertEquals(0, budget.acquire(199, 1, true))
        assertEquals(1, budget.acquire(200, 1, true))
    }

    @Test fun adapterRetryIsBoundedInsteadOfRemovingLiveRequest() {
        assertEquals(1000L, callbackRetryDelay(1))
        assertEquals(2000L, callbackRetryDelay(2))
        assertEquals(30000L, callbackRetryDelay(100))
    }
}
