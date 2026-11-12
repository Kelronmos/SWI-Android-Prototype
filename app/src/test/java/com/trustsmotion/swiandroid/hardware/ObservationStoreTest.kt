package com.trustsmotion.swiandroid.hardware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationStoreTest {

    @Test
    fun exportPreservesNonClaimsAndNotAuthorized() {
        val store = ObservationStore(capacity = 10)
        store.add(
            HardwareObservation(
                observationId = "o1",
                sourceType = HardwareObservation.SourceType.PROXIMITY,
                sourceName = "proximity",
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.BOUND,
                valueSummary = "distance=1.0",
                monotonicTimestampNs = 1L,
                wallClockEpochMs = 1L,
                deviceFingerprint = "fp",
                authorization = "NOT_AUTHORIZED"
            )
        )
        val json = store.exportJson("fp")
        assertTrue(json.contains("\"observation_is_authorization\": false"))
        assertTrue(json.contains("\"swi_authorized\": false"))
        assertTrue(json.contains("\"production_authorized\": false"))
        assertTrue(json.contains("\"proven\": false"))
        assertTrue(json.contains("\"sealed\": false"))
        assertFalse(json.contains("\"swi_authorized\": true"))
        assertFalse(json.contains("PRODUCTION_AUTHORIZED\": true"))
    }

    @Test
    fun capacityEvictsOldest() {
        val store = ObservationStore(capacity = 2)
        fun make(id: String) = HardwareObservation(
            observationId = id,
            sourceType = HardwareObservation.SourceType.SYSTEM,
            sourceName = "sys",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.BOUND,
            valueSummary = id,
            monotonicTimestampNs = 1L,
            wallClockEpochMs = 1L,
            deviceFingerprint = "fp"
        )
        store.add(make("a"))
        store.add(make("b"))
        store.add(make("c"))
        val ids = store.snapshot().map { it.observationId }
        assertFalse(ids.contains("a"))
        assertTrue(ids.contains("b"))
        assertTrue(ids.contains("c"))
    }
}
