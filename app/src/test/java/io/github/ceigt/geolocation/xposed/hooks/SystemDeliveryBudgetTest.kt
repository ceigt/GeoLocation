package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class SystemDeliveryBudgetTest {
    @org.junit.Test fun pausedRequestHasFiniteCleanupDeadline() {
        val budget = SystemDeliveryBudget(100L, 1000L, 5000L, 2)
        org.junit.Assert.assertEquals(4000L, budget.remainingMillis(1100L))
        org.junit.Assert.assertEquals(0L, budget.remainingMillis(6000L))
        val unlimited = SystemDeliveryBudget(100L, 1000L, Long.MAX_VALUE, 1)
        org.junit.Assert.assertEquals(Long.MAX_VALUE, unlimited.remainingMillis(1100L))
        unlimited.sent(1100L)
        org.junit.Assert.assertEquals(0L, unlimited.remainingMillis(1100L))
    }
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
    @org.junit.Test fun nativeBatchAndSupplementShareRemainingCount() {
        val budget = SystemDeliveryBudget(0, 1000, 10000, 3)
        org.junit.Assert.assertEquals(1, budget.acquire(0, 1, true))
        org.junit.Assert.assertEquals(0, budget.acquire(500, 3, true))
        org.junit.Assert.assertEquals(2, budget.acquire(1000, 5, true))
        org.junit.Assert.assertEquals(0, budget.acquire(2000, 1, false))
    }
    @org.junit.Test fun concurrentSourcesCannotExceedSingleUpdate() {
        val budget = SystemDeliveryBudget(0, 1000, 10000, 1)
        val start = java.util.concurrent.CountDownLatch(1)
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val workers = (1..16).map { Thread { start.await(); accepted.addAndGet(budget.acquire(0, 1, true)) }.apply { start() } }
        start.countDown()
        workers.forEach { it.join(2000); org.junit.Assert.assertFalse(it.isAlive) }
        org.junit.Assert.assertEquals(1, accepted.get())
    }
}
