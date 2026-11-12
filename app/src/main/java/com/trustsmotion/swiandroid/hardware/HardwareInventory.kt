package com.trustsmotion.swiandroid.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import java.util.UUID

/**
 * Discovers device hardware and reports PRESENT / ABSENT / UNKNOWN explicitly.
 * Presence alone never implies binding, verification, or authorization.
 */
class HardwareInventory(private val context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun deviceFingerprint(): String {
        return listOf(
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.VERSION.SDK_INT.toString()
        ).joinToString("|")
    }

    fun discover(): List<HardwareObservation> {
        val nowWall = System.currentTimeMillis()
        val nowMono = SystemClock.elapsedRealtimeNanos()
        val fp = deviceFingerprint()
        val out = mutableListOf<HardwareObservation>()

        out += sensorObservation(
            type = HardwareObservation.SourceType.PROXIMITY,
            androidType = Sensor.TYPE_PROXIMITY,
            name = "proximity",
            nowWall = nowWall,
            nowMono = nowMono,
            fp = fp
        )
        out += sensorObservation(
            type = HardwareObservation.SourceType.ACCELEROMETER,
            androidType = Sensor.TYPE_ACCELEROMETER,
            name = "accelerometer",
            nowWall = nowWall,
            nowMono = nowMono,
            fp = fp
        )
        out += sensorObservation(
            type = HardwareObservation.SourceType.GYROSCOPE,
            androidType = Sensor.TYPE_GYROSCOPE,
            name = "gyroscope",
            nowWall = nowWall,
            nowMono = nowMono,
            fp = fp
        )
        out += sensorObservation(
            type = HardwareObservation.SourceType.LIGHT,
            androidType = Sensor.TYPE_LIGHT,
            name = "ambient_light",
            nowWall = nowWall,
            nowMono = nowMono,
            fp = fp
        )

        out += touchCapability(nowWall, nowMono, fp)
        out += biometricCapability(nowWall, nowMono, fp)
        out += keystoreCapability(nowWall, nowMono, fp)

        return out
    }

    private fun sensorObservation(
        type: HardwareObservation.SourceType,
        androidType: Int,
        name: String,
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val sensor = sensorManager.getDefaultSensor(androidType)
        return if (sensor == null) {
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = type,
                sourceName = name,
                capabilityState = HardwareObservation.CapabilityState.ABSENT,
                bindingState = HardwareObservation.BindingState.UNBOUND,
                valueSummary = "ABSENT",
                monotonicTimestampNs = nowMono,
                wallClockEpochMs = nowWall,
                deviceFingerprint = fp,
                detail = mapOf("android_sensor_type" to androidType.toString())
            )
        } else {
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = type,
                sourceName = name,
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.UNBOUND,
                valueSummary = "PRESENT name=${sensor.name}",
                monotonicTimestampNs = nowMono,
                wallClockEpochMs = nowWall,
                deviceFingerprint = fp,
                detail = mapOf(
                    "android_sensor_type" to androidType.toString(),
                    "vendor" to (sensor.vendor ?: ""),
                    "version" to sensor.version.toString(),
                    "resolution" to sensor.resolution.toString(),
                    "max_range" to sensor.maximumRange.toString(),
                    "power_ma" to sensor.power.toString()
                )
            )
        }
    }

    private fun touchCapability(
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val hasTouch = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TOUCHSCREEN
        )
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.TOUCH,
            sourceName = "touchscreen",
            capabilityState = if (hasTouch) {
                HardwareObservation.CapabilityState.PRESENT
            } else {
                HardwareObservation.CapabilityState.ABSENT
            },
            bindingState = HardwareObservation.BindingState.UNBOUND,
            valueSummary = if (hasTouch) "PRESENT" else "ABSENT",
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            detail = mapOf("feature" to PackageManager.FEATURE_TOUCHSCREEN)
        )
    }

    private fun biometricCapability(
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        val (cap, summary) = when (result) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                HardwareObservation.CapabilityState.PRESENT to "BIOMETRIC_STRONG_AVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                HardwareObservation.CapabilityState.PRESENT to "PRESENT_NONE_ENROLLED"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                HardwareObservation.CapabilityState.ABSENT to "NO_HARDWARE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                HardwareObservation.CapabilityState.UNAVAILABLE to "HW_UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                HardwareObservation.CapabilityState.UNAVAILABLE to "SECURITY_UPDATE_REQUIRED"
            else ->
                HardwareObservation.CapabilityState.UNKNOWN to "CODE_$result"
        }
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.BIOMETRIC,
            sourceName = "biometric_strong",
            capabilityState = cap,
            bindingState = HardwareObservation.BindingState.UNBOUND,
            valueSummary = summary,
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            detail = mapOf("can_authenticate_code" to result.toString())
        )
    }

    private fun keystoreCapability(
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.KEYSTORE,
            sourceName = "AndroidKeyStore",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.UNBOUND,
            valueSummary = "API_PRESENT",
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            detail = mapOf(
                "note" to "Keystore API present; hardware-backed status requires key-specific probe"
            )
        )
    }
}
