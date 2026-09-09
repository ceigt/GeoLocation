package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class SystemDeliveryBudgetTest {
    @Test fun firstDeliveryIsImmediateAndRequestedIntervalIsRespected() {
        val budget = SystemDeliveryBudget(100, 5000, Long.MAX_VALUE, 10)
        assertTrue(budget.due(100))
        budget.sent(100)
        assertFalse(budget.due(5099))
        assertTrue(budget.due(5100))
    }
    @Test fun rapidRequestsAreBoundedAndMaxUpdatesExpires() {
        val budget = SystemDeliveryBudget(0, 0, Long.MAX_VALUE, 2)
        budget.sent(0)
        assertFalse(budget.due(999))
        assertTrue(budget.due(1000))
        budget.sent(1000)
        assertTrue(budget.expired(1001))
    }
    @Test fun durationExpiresEvenWhilePaused() {
        val budget = SystemDeliveryBudget(50, 1000, 5000, Int.MAX_VALUE)
        assertFalse(budget.expired(5049))
        assertTrue(budget.expired(5050))
    }
}
