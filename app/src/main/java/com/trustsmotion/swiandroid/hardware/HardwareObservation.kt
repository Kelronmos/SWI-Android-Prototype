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
 *   UNKNOWN != FALSE
 *   STALE != VALID
 *   OBSERVATION != AUTHORIZATION
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
    val staleAfterMs: Long = 5_000L
) {
    enum class SourceType {
        TOUCH,
        PROXIMITY,
        ACCELEROMETER,
        GYROSCOPE,
        LIGHT,
        BIOMETRIC,
        KEYSTORE,
        INVENTORY,
        SYSTEM
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
        UNKNOWN
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

    /** Explicit non-claims — never upgrade from observation alone. */
    fun nonClaims(): Map<String, Boolean> = mapOf(
        "hardware_present_implies_bound" to false,
        "hardware_bound_implies_sensor_verified" to false,
        "sensor_verified_implies_human_authorized" to false,
        "human_authorized_implies_execution_authorized" to false,
        "observation_is_authorization" to false,
        "swi_authorized" to false,
        "production_authorized" to false,
        "proven" to false,
        "sealed" to false
    )

    fun toExportMap(): Map<String, Any> = linkedMapOf(
        "observation_id" to observationId,
        "source_type" to sourceType.name,
        "source_name" to sourceName,
        "capability_state" to capabilityState.name,
        "binding_state" to bindingState.name,
        "value_summary" to valueSummary,
        "monotonic_timestamp_ns" to monotonicTimestampNs,
        "wall_clock_epoch_ms" to wallClockEpochMs,
        "device_fingerprint" to deviceFingerprint,
        "detail" to detail,
        "stale_after_ms" to staleAfterMs,
        "non_claims" to nonClaims()
    )
}
