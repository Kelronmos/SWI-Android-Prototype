package com.trustsmotion.swiandroid.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.trustsmotion.swiandroid.hardware.HardwareObservation
import java.util.UUID
import kotlin.random.Random

/**
 * Adjustable 5-4-3-2-1 guided session.
 * Wellness / accessibility aid only. Not identity, not authorization.
 */
class GuidedBreathingSession(
    private val deviceFingerprint: String,
    private val speak: (String) -> Unit,
    private val onObservation: (HardwareObservation) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = true
    private var stepIndex = 0

    /** Timing in milliseconds between spoken steps (user adjustable). */
    var stepDelayMs: Long = 4_000L

    private val fiveWordPool = listOf(
        "forgiveness", "healing", "kindness", "learning", "sharing",
        "patience", "courage", "gratitude", "compassion", "hope",
        "gentleness", "curiosity", "trust", "calm", "warmth"
    )
    private val threeWordPrompts = listOf(
        "You are safe",
        "Breathe with ease",
        "Rest and recover",
        "Open to learning",
        "Soft and steady"
    )
    private val twoWordPrompts = listOf(
        "Release tension",
        "Soft shoulders",
        "Quiet mind",
        "Gentle heart"
    )
    private val affirmations = listOf(
        "I meet this moment with care.",
        "I choose kindness toward myself and others.",
        "I can pause, learn, and continue."
    )

    fun start() {
        stopped = false
        stepIndex = 0
        emit("SESSION_START")
        runStep()
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        emit("SESSION_STOP")
    }

    fun pause() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        emit("SESSION_PAUSE")
    }

    private fun runStep() {
        if (stopped) return
        val script = buildScript()
        if (stepIndex >= script.size) {
            emit("SESSION_COMPLETE")
            stopped = true
            return
        }
        val line = script[stepIndex]
        speak(line)
        emit("STEP_$stepIndex", line)
        stepIndex++
        handler.postDelayed({ runStep() }, stepDelayMs)
    }

    private fun buildScript(): List<String> {
        val five = fiveWordPool.shuffled(Random).take(5)
        return listOf(
            "Five words: ${five.joinToString(", ")}.",
            "Gentle sustained S sound. Soft and easy.",
            threeWordPrompts.random(),
            "Gentle sustained I sound. Comfortable only.",
            twoWordPrompts.random(),
            affirmations.random()
        )
    }

    private fun emit(summary: String, line: String = "") {
        onObservation(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = HardwareObservation.SourceType.BREATHING,
                sourceName = "guided_breathing",
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.BOUND,
                valueSummary = if (line.isBlank()) summary else "$summary | $line",
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = deviceFingerprint,
                authorization = "NOT_AUTHORIZED",
                evidence = "NOT_YET_VERIFIED",
                detail = mapOf(
                    "note" to "Wellness session only; not identity or authorization"
                )
            )
        )
    }
}
