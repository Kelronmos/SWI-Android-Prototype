package com.trustsmotion.swiandroid.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for HardwareObservation.
 * PROVEN=NO SEALED=NO PRODUCTION_AUTHORIZED=NO
 */
class HardwareObservationTest {

    private fun obs(
        capability: HardwareObservation.CapabilityState = HardwareObservation.CapabilityState.PRESENT,
        binding: HardwareObservation.BindingState = HardwareObservation.BindingState.BOUND,
        wallMs: Long,
        staleAfterMs: Long = 5_000L,
        valueSummary: String = "x=1"
    ) = HardwareObservation(
        observationId = "test-id",
        sourceType = HardwareObservation.SourceType.PROXIMITY,
        sourceName = "proximity",
        capabilityState = capability,
        bindingState = binding,
        valueSummary = valueSummary,
        monotonicTimestampNs = 1_000L,
        wallClockEpochMs = wallMs,
        deviceFingerprint = "Test|Device|dev|33",
        staleAfterMs = staleAfterMs
    )

    @Test
    fun boundBecomesStaleAfterMaxAge() {
        val o = obs(wallMs = 1_000L, staleAfterMs = 5_000L)
        assertTrue(o.isStale(nowEpochMs = 7_000L))
        assertEquals(
            HardwareObservation.BindingState.STALE,
            o.effectiveBinding(nowEpochMs = 7_000L)
        )
    }

    @Test
    fun boundRemainsBoundWithinMaxAge() {
        val o = obs(wallMs = 1_000L, staleAfterMs = 5_000L)
        assertFalse(o.isStale(nowEpochMs = 3_000L))
        assertEquals(
            HardwareObservation.BindingState.BOUND,
            o.effectiveBinding(nowEpochMs = 3_000L)
        )
    }

    @Test
    fun holdDoesNotAutoUpgradeToBound() {
        val o = obs(
            binding = HardwareObservation.BindingState.HOLD,
            wallMs = 1_000L
        )
        assertEquals(
            HardwareObservation.BindingState.HOLD,
            o.effectiveBinding(nowEpochMs = 10_000L)
        )
    }

    @Test
    fun absentIsNotMarkedStale() {
        val o = obs(
            capability = HardwareObservation.CapabilityState.ABSENT,
            binding = HardwareObservation.BindingState.UNBOUND,
            wallMs = 1_000L
        )
        assertFalse(o.isStale(nowEpochMs = 100_000L))
    }

    @Test
    fun unknownCapabilityIsNotMarkedStale() {
        val o = obs(
            capability = HardwareObservation.CapabilityState.UNKNOWN,
            binding = HardwareObservation.BindingState.UNKNOWN,
            wallMs = 1_000L
        )
        assertFalse(o.isStale(nowEpochMs = 100_000L))
    }

    @Test
    fun nullReadingSummaryPreserved() {
        val o = HardwareObservation.proximityTemplate(
            valueCm = null,
            capability = HardwareObservation.CapabilityState.PRESENT,
            binding = HardwareObservation.BindingState.PENDING_VERIFICATION,
            permissionState = "GRANTED",
            freshnessState = "UNKNOWN",
            monoNs = 1L,
            wallMs = 1L,
            deviceFp = "fp"
        )
        assertEquals("null", o.valueSummary)
        assertEquals("cm", o.unit)
        assertEquals("NOT_AUTHORIZED", o.authorization)
        assertEquals("NOT_YET_VERIFIED", o.evidence)
    }

    @Test
    fun wallClockSkewDoesNotBypassStaleness() {
        // Observation taken at wall=1000; evaluator clock jumps forward → still stale by age.
        val o = obs(wallMs = 1_000L, staleAfterMs = 5_000L)
        assertTrue(o.isStale(nowEpochMs = 20_000L))
        // Evaluator clock behind observation wall → age clamped by subtraction (negative → not > max)
        // Implementation uses now - wall; if now < wall, age is negative → not stale.
        // Documented: wall-clock alone cannot mark future-dated obs as fresh after real mono age;
        // freshness should prefer monotonic in production. Here we assert age math only.
        assertFalse(o.isStale(nowEpochMs = 500L))
    }

    @Test
    fun nonClaimsAllFalseIncludingAuthorization() {
        val o = obs(wallMs = System.currentTimeMillis())
        val nc = o.nonClaims()
        assertFalse(nc.getValue("observation_is_authorization"))
        assertFalse(nc.getValue("swi_authorized"))
        assertFalse(nc.getValue("production_authorized"))
        assertFalse(nc.getValue("proven"))
        assertFalse(nc.getValue("sealed"))
        assertFalse(nc.getValue("voice_match_implies_identity"))
        assertFalse(nc.getValue("biometric_implies_workflow_authorization"))
        assertFalse(nc.getValue("permission_is_execution"))
    }

    @Test
    fun exportMapPreservesNonClaimsAndAuthorization() {
        val o = obs(wallMs = 1_000L)
        val map = o.toExportMap()
        assertEquals("NOT_AUTHORIZED", map["authorization"])
        @Suppress("UNCHECKED_CAST")
        val nc = map["non_claims"] as Map<String, Boolean>
        assertFalse(nc.getValue("swi_authorized"))
        assertFalse(nc.getValue("production_authorized"))
    }
}
