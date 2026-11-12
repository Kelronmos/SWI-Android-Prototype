package com.trustsmotion.swiandroid.voice

import com.trustsmotion.swiandroid.hardware.HardwareObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedBreathingSessionTest {

    @Test
    fun startStopAndPauseEmitObservationsWithoutAuthorization() {
        val spoken = mutableListOf<String>()
        val observed = mutableListOf<HardwareObservation>()

        val session = GuidedBreathingSession(
            deviceFingerprint = "Test|Device|dev|33",
            speak = { spoken.add(it) },
            onObservation = { observed.add(it) }
        )
        session.stepDelayMs = 60_000L // avoid auto-advance during test

        session.start()
        assertTrue(observed.any { it.valueSummary.contains("SESSION_START") })

        session.pause()
        assertTrue(observed.any { it.valueSummary.contains("SESSION_PAUSE") })

        session.stop()
        assertTrue(observed.any { it.valueSummary.contains("SESSION_STOP") })

        for (o in observed) {
            assertEquals(HardwareObservation.SourceType.BREATHING, o.sourceType)
            assertEquals("NOT_AUTHORIZED", o.authorization)
            assertFalse(o.nonClaims().getValue("swi_authorized"))
            assertFalse(o.nonClaims().getValue("production_authorized"))
        }
    }
}
