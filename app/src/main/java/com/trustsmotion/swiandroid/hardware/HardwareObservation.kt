package com.trustsmotion.swiandroid.hardware

/**
 * Single hardware observation record.
 *
 * STATUS: PROTOTYPE
 * PROVEN = false · SEALED = false · PRODUCTION_AUTHORIZED = false
 *
 * Distinctions enforced by field semantics:
 *   HARDWARE_PRESENT != HARDWARE_BOUND
 *   HARDWARE_BOUND != SENSOR_VERIFIED
 *   SENSOR_VERIFIED != HUMAN_AUTHORIZED
 *   HUMAN_AUTHORIZED != EXECUTION_AUTHORIZED
 *   HARDWARE_DISCOVERED != HARDWARE_BOUND
 *   SENSOR_READING != EVIDENCE
 *   VOICE_MATCH != HUMAN_IDENTITY
 *   BIOMETRIC_AUTHENTICATION != WORKFLOW_AUTHORIZATION
 *   UNKNOWN != FALSE
 *   STALE != VALID
 *   OBSERVATION != AUTHORIZATION
 *   OBSERVATION != PERMISSION
 *   PERMISSION != EXECUTION
 */
data class HardwareObservation(
    val observationId: String,
    val sourceType: SourceType,
    val sourceName: String,
    val capabilityState: CapabilityState,
    val bindingState: BindingState,
    val valueSummary: String,
    val monotonicTimestampNs: Long,
    val wallClockEpochMs: Long,
    val deviceFingerprint: String,
    val detail: Map<String, String> = emptyMap(),
    val unit: String? = null,
    val permission: String = "NOT_APPLICABLE",
    val freshness: String = "UNKNOWN",
    val evidence: String = "NOT_YET_VERIFIED",
    val authorization: String = "NOT_AUTHORIZED",
    val staleAfterMs: Long = 5_000L
) {
    enum class SourceType {
        TOUCH,
        PROXIMITY,
        ACCELEROMETER,
        GYROSCOPE,
        LIGHT,
        BAROMETER,
        HUMIDITY,
        TEMPERATURE,
        MAGNETOMETER,
        ROTATION_VECTOR,
        GPS,
        BIOMETRIC,
        KEYSTORE,
        CAMERA,
        MICROPHONE,
        BATTERY,
        WIFI,
        CELLULAR,
        BLUETOOTH,
        NFC,
        VOICE,
        BREATHING,
        FACE_POSITION,
        INVENTORY,
        SYSTEM,
        ELECTRONICS
    }

    enum class CapabilityState {
        PRESENT,
        ABSENT,
        UNKNOWN,
        PERMISSION_DENIED,
        UNAVAILABLE,
        FAILED
    }

    enum class BindingState {
        BOUND,
        UNBOUND,
        STALE,
        DISCONNECTED,
        HOLD,
        UNKNOWN,
        PENDING_VERIFICATION
    }

    fun isStale(nowEpochMs: Long): Boolean {
        if (capabilityState == CapabilityState.ABSENT ||
            capabilityState == CapabilityState.UNKNOWN ||
            capabilityState == CapabilityState.PERMISSION_DENIED
        ) {
            return false
        }
        return nowEpochMs - wallClockEpochMs > staleAfterMs
    }

    fun effectiveBinding(nowEpochMs: Long): BindingState {
        if (isStale(nowEpochMs) && bindingState == BindingState.BOUND) {
            return BindingState.STALE
        }
        return bindingState
    }

    fun nonClaims(): Map<String, Boolean> = mapOf(
        "hardware_present_implies_bound" to false,
        "hardware_bound_implies_sensor_verified" to false,
        "sensor_verified_implies_human_authorized" to false,
        "human_authorized_implies_execution_authorized" to false,
        "voice_match_implies_identity" to false,
        "biometric_implies_workflow_authorization" to false,
        "observation_is_authorization" to false,
        "observation_is_permission" to false,
        "permission_is_execution" to false,
        "swi_authorized" to false,
        "production_authorized" to false,
        "proven" to false,
        "sealed" to false
    )

    fun toExportMap(): Map<String, Any?> = linkedMapOf(
        "observation_id" to observationId,
        "source_type" to sourceType.name,
        "source_name" to sourceName,
        "capability_state" to capabilityState.name,
        "binding_state" to bindingState.name,
        "value_summary" to valueSummary,
        "unit" to unit,
        "permission" to permission,
        "freshness" to freshness,
        "evidence" to evidence,
        "authorization" to authorization,
        "monotonic_timestamp_ns" to monotonicTimestampNs,
        "wall_clock_epoch_ms" to wallClockEpochMs,
        "device_fingerprint" to deviceFingerprint,
        "detail" to detail,
        "stale_after_ms" to staleAfterMs,
        "non_claims" to nonClaims()
    )

    /** Canonical proximity-style observation template (value may be null). */
    companion object {
        fun proximityTemplate(
            valueCm: Float?,
            capability: CapabilityState,
            binding: BindingState,
            permissionState: String,
            freshnessState: String,
            monoNs: Long,
            wallMs: Long,
            deviceFp: String,
            sourceName: String = "proximity"
        ): HardwareObservation {
            return HardwareObservation(
                observationId = java.util.UUID.randomUUID().toString(),
                sourceType = SourceType.PROXIMITY,
                sourceName = sourceName,
                capabilityState = capability,
                bindingState = binding,
                valueSummary = valueCm?.toString() ?: "null",
                monotonicTimestampNs = monoNs,
                wallClockEpochMs = wallMs,
                deviceFingerprint = deviceFp,
                unit = "cm",
                permission = permissionState,
                freshness = freshnessState,
                evidence = "NOT_YET_VERIFIED",
                authorization = "NOT_AUTHORIZED"
            )
        }
    }
}
