package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class SupplementalDeliveryTest {
    @Test fun preparationFailureLeavesSingleFixAndDistanceAllowanceForRetry() {
        val budget = SystemDeliveryBudget(0, 1000, 10000, 1)
        val policy = LocationDeliveryPolicy<Int>(budget, 100f) { a, b -> kotlin.math.abs(a - b).toFloat() }
        var sent = 0
        try {
            deliverPrepared<Int>({ throw NoSuchMethodException("availability") },
                { policy.accept(0, listOf(it), true).isNotEmpty() }, { sent++ })
            fail("Expected preparation failure")
        } catch (_: NoSuchMethodException) { }
        assertTrue(budget.due(1000))
        assertTrue(deliverPrepared({ 1 }, { policy.accept(1000, listOf(it), true).isNotEmpty() }, { sent++ }))
        assertEquals(1, sent)
        assertFalse(deliverPrepared({ 200 }, { policy.accept(2000, listOf(it), true).isNotEmpty() }, { sent++ }))
        assertEquals(1, sent)
    }

    @Test fun attemptedCallbackIsNotRefundedEvenIfBinderThrows() {
        val budget = SystemDeliveryBudget(0, 1000, 10000, 1)
        var attempts = 0
        try {
            deliverPrepared({ Unit }, { budget.acquire(0, 1, true) == 1 }, {
                attempts++
                throw IllegalStateException("may have reached client")
            })
            fail("Expected callback failure")
        } catch (_: IllegalStateException) { }
        assertFalse(deliverPrepared({ Unit }, { budget.acquire(1000, 1, true) == 1 }, { attempts++ }))
        assertEquals(1, attempts)
    }

    @Test fun slowPreparationDoesNotExtendDeadline() {
        val budget = SystemDeliveryBudget(0, 1000, 500, 1)
        var now = 0L
        assertFalse(deliverPrepared({ now = 1000; Unit }, { budget.acquire(now, 1, true) == 1 }, { fail("Expired request sent") }))
    }

    @Test fun onlyNativeEventsForTrackedSimulationAreSuppressed() {
        assertTrue(suppressNativeGnss(true, true, true, false))
        assertFalse(suppressNativeGnss(true, true, true, true))
        assertFalse(suppressNativeGnss(true, false, true, false))
        assertFalse(suppressNativeGnss(true, true, false, false))
        assertFalse(suppressNativeGnss(false, true, true, false))
    }

    @Test fun locationForegroundServiceRequiresEvaluatedWhileInUseAuthorization() {
        assertTrue(ForegroundLocationAccess.applicationForeground(false, true, 4, 0))
        assertFalse(ForegroundLocationAccess.applicationForeground(false, true, 4, 1))
        assertFalse(ForegroundLocationAccess.applicationForeground(false, true, 4, 2))
        assertFalse(ForegroundLocationAccess.applicationForeground(false, false, 4, 0))
        assertFalse(ForegroundLocationAccess.applicationForeground(false, true, 0, 0))
        assertTrue(ForegroundLocationAccess.applicationForeground(true, false, 4, 1))
    }

    @Test fun foregroundEligibilityNeverOverridesStoredDenial() {
        assertFalse(SyntheticLocationAccess.allowed(1, null, 0, true))
        assertFalse(SyntheticLocationAccess.allowed(2, null, 0, true))
        assertTrue(SyntheticLocationAccess.allowed(4, null, 0, true))
        assertFalse(SyntheticLocationAccess.allowed(4, null, 0, false))
    }
}
