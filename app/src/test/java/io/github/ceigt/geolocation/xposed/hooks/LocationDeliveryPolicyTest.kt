package io.github.ceigt.geolocation.xposed.hooks
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class LocationDeliveryPolicyTest {
    private fun policy(max: Int = 3) = LocationDeliveryPolicy<Float>(SystemDeliveryBudget(0,1000,20000,max),10f) { a,b -> abs(a-b) }
    @Test fun stationaryFixesDoNotSpendTheRemainingCount() {
        val p=policy(2)
        assertEquals(listOf(0f),p.accept(0,listOf(0f),true))
        assertTrue(p.accept(1000,listOf(0f,5f),true).isEmpty())
        assertEquals(listOf(10f),p.accept(2000,listOf(10f),true))
        assertTrue(p.accept(3000,listOf(30f),true).isEmpty())
    }
    @Test fun rejectedIntervalDoesNotMoveDistanceAnchor() {
        val p=policy()
        p.accept(0,listOf(0f),true)
        assertTrue(p.accept(500,listOf(20f),true).isEmpty())
        assertEquals(listOf(10f),p.accept(1000,listOf(10f),true))
    }
    @Test fun batchDistanceFilteringAndBudgetUseOnlyAdmittedFixes() {
        val p=policy(2)
        assertEquals(listOf(0f,10f),p.accept(0,listOf(0f,2f,10f,11f,20f),true))
        assertTrue(p.accept(1000,listOf(20f),true).isEmpty())
    }
    @Test fun nativeFixBecomesAnchorWhenSimulationStarts() {
        val p=policy()
        p.accept(0,listOf(100f),false)
        assertTrue(p.accept(1000,listOf(105f),true).isEmpty())
        assertEquals(listOf(110f),p.accept(2000,listOf(110f),true))
    }
}
