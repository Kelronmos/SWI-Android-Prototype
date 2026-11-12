package com.trustsmotion.swiandroid.voice

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.trustsmotion.swiandroid.hardware.HardwareObservation
import java.util.Locale
import java.util.UUID

/**
 * Voice verification helper.
 *
 * Reads a verification message via TTS. Speech recognition comparison is optional
 * and reported as MATCH / MISMATCH / TIMEOUT / UNAVAILABLE observations only.
 *
 * VOICE_MATCH != HUMAN_IDENTITY
 * BIOMETRIC_AUTHENTICATION != WORKFLOW_AUTHORIZATION
 * OBSERVATION != AUTHORIZATION
 */
class VoiceVerificationModule(
    private val context: Context,
    private val deviceFingerprint: String,
    private val onObservation: (HardwareObservation) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        const val DEFAULT_MESSAGE =
            "SWI verification. I confirm this is a local observation only. " +
                "This does not authorize any workflow action."
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastMessage: String = DEFAULT_MESSAGE

    fun init() {
        if (tts == null) {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    emit(
                        summary = "TTS_STARTED",
                        binding = HardwareObservation.BindingState.BOUND,
                        detail = mapOf("utterance_id" to (utteranceId ?: ""))
                    )
                }

                override fun onDone(utteranceId: String?) {
                    emit(
                        summary = "TTS_DONE",
                        binding = HardwareObservation.BindingState.UNBOUND,
                        detail = mapOf("utterance_id" to (utteranceId ?: ""))
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    emit(
                        summary = "TTS_ERROR",
                        binding = HardwareObservation.BindingState.HOLD,
                        capability = HardwareObservation.CapabilityState.FAILED,
                        detail = mapOf("utterance_id" to (utteranceId ?: ""))
                    )
                }
            })
            emit(
                summary = "TTS_READY",
                binding = HardwareObservation.BindingState.UNBOUND,
                capability = HardwareObservation.CapabilityState.PRESENT
            )
        } else {
            emit(
                summary = "TTS_UNAVAILABLE",
                binding = HardwareObservation.BindingState.UNBOUND,
                capability = HardwareObservation.CapabilityState.UNAVAILABLE
            )
        }
    }

    fun verificationMessage(): String = lastMessage

    fun setMessage(message: String) {
        lastMessage = message.ifBlank { DEFAULT_MESSAGE }
    }

    fun speak(message: String = lastMessage) {
        lastMessage = message
        if (!ready) {
            emit(
                summary = "TTS_NOT_READY",
                binding = HardwareObservation.BindingState.HOLD,
                capability = HardwareObservation.CapabilityState.UNAVAILABLE
            )
            return
        }
        val id = UUID.randomUUID().toString()
        val params = Bundle()
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    fun pause() {
        tts?.stop()
        emit(
            summary = "TTS_STOPPED",
            binding = HardwareObservation.BindingState.HOLD
        )
    }

    /**
     * Compare a user-provided transcript to the expected message.
     * This is a local string comparison observation — not identity proof.
     */
    fun compareTranscript(transcript: String): HardwareObservation {
        val expected = lastMessage.trim().lowercase()
        val got = transcript.trim().lowercase()
        val match = expected.isNotEmpty() && got == expected
        val summary = when {
            got.isEmpty() -> "TRANSCRIPT_EMPTY"
            match -> "VOICE_MATCH"
            else -> "VOICE_MISMATCH"
        }
        val obs = HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.VOICE,
            sourceName = "voice_verification",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.PENDING_VERIFICATION,
            valueSummary = summary,
            monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
            wallClockEpochMs = System.currentTimeMillis(),
            deviceFingerprint = deviceFingerprint,
            permission = "MICROPHONE_USER_CONTROLLED",
            freshness = "LIVE",
            evidence = "NOT_YET_VERIFIED",
            authorization = "NOT_AUTHORIZED",
            detail = mapOf(
                "expected_len" to expected.length.toString(),
                "transcript_len" to got.length.toString(),
                "note" to "VOICE_MATCH != HUMAN_IDENTITY; not workflow authorization"
            )
        )
        onObservation(obs)
        return obs
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun emit(
        summary: String,
        binding: HardwareObservation.BindingState,
        capability: HardwareObservation.CapabilityState = HardwareObservation.CapabilityState.PRESENT,
        detail: Map<String, String> = emptyMap()
    ) {
        onObservation(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = HardwareObservation.SourceType.VOICE,
                sourceName = "tts",
                capabilityState = capability,
                bindingState = binding,
                valueSummary = summary,
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = deviceFingerprint,
                authorization = "NOT_AUTHORIZED",
                evidence = "NOT_YET_VERIFIED",
                detail = detail
            )
        )
    }
}
