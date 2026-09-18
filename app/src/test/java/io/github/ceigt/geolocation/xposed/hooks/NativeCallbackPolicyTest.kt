package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class NativeCallbackPolicyTest {
    @Test fun unknownCallbacksAreBlockedOnlyDuringSimulation() {
        val policy = NativeCallbackPolicy<Any>()
        val token = Any()
        assertFalse(policy.mayPassUntracked(true, token))
        assertFalse(policy.mayPassUntracked(true, null))
        assertTrue(policy.mayPassUntracked(false, token))
        assertTrue(policy.mayPassUntracked(false, null))
    }

    @Test fun systemExemptionDoesNotExtendToAnotherCallback() {
        val policy = NativeCallbackPolicy<Any>()
        val system = Any()
        policy.record(system, true)
        assertTrue(policy.mayPassUntracked(true, system))
        assertFalse(policy.mayPassUntracked(true, Any()))
    }

    @Test fun registrationByAnOrdinaryCallerRevokesPreviousExemption() {
        val policy = NativeCallbackPolicy<Any>()
        val token = Any()
        policy.record(token, true)
        policy.record(token, false)
        assertFalse(policy.mayPassUntracked(true, token))
        assertTrue(policy.mayPassUntracked(false, token))
    }
}
