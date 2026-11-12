package com.trustsmotion.swiandroid.voice

import com.trustsmotion.swiandroid.hardware.HardwareObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure observation semantics for voice transcript comparison.
 * Does not construct Android TTS (no Context required).
 */
class VoiceMatchObservationTest {

    private fun compare(expected: String, transcript: String): HardwareObservation {
        val exp = expected.trim().lowercase()
        val got = transcript.trim().lowercase()
        val summary = when {
            got.isEmpty() -> "TRANSCRIPT_EMPTY"
            exp.isNotEmpty() && got == exp -> "VOICE_MATCH"
            else -> "VOICE_MISMATCH"
        }
        return HardwareObservation(
            observationId = "v1",
            sourceType = HardwareObservation.SourceType.VOICE,
            sourceName = "voice_verification",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.PENDING_VERIFICATION,
            valueSummary = summary,
            monotonicTimestampNs = 1L,
            wallClockEpochMs = 1L,
            deviceFingerprint = "fp",
            authorization = "NOT_AUTHORIZED",
            evidence = "NOT_YET_VERIFIED"
        )
    }

    @Test
    fun matchIsObservationNotIdentity() {
        val msg = "SWI verification. I confirm this is a local observation only."
        val o = compare(msg, msg)
        assertEquals("VOICE_MATCH", o.valueSummary)
        assertEquals("NOT_AUTHORIZED", o.authorization)
        assertFalse(o.nonClaims().getValue("voice_match_implies_identity"))
        assertFalse(o.nonClaims().getValue("swi_authorized"))
    }

    @Test
    fun mismatchRemainsNotAuthorized() {
        val o = compare("expected phrase", "different phrase")
        assertEquals("VOICE_MISMATCH", o.valueSummary)
        assertEquals("NOT_AUTHORIZED", o.authorization)
    }

    @Test
    fun emptyTranscriptNotMatch() {
        val o = compare("hello", "")
        assertEquals("TRANSCRIPT_EMPTY", o.valueSummary)
    }

    @Test
    fun biometricNonClaimPresentOnObservationModel() {
        val o = compare("x", "x")
        assertTrue(o.nonClaims().containsKey("biometric_implies_workflow_authorization"))
        assertFalse(o.nonClaims().getValue("biometric_implies_workflow_authorization"))
    }
}
