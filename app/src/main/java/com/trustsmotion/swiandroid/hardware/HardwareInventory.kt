package com.trustsmotion.swiandroid.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import java.util.UUID

/**
 * Dynamic hardware discovery. Never assumes a sensor exists because the API exists.
 * Presence alone never implies binding, verification, or authorization.
 */
class HardwareInventory(private val context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pm = context.packageManager

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

        // Motion / environment sensors
        out += sensorObservation(HardwareObservation.SourceType.PROXIMITY, Sensor.TYPE_PROXIMITY, "proximity", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.ACCELEROMETER, Sensor.TYPE_ACCELEROMETER, "accelerometer", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.GYROSCOPE, Sensor.TYPE_GYROSCOPE, "gyroscope", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.LIGHT, Sensor.TYPE_LIGHT, "ambient_light", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.BAROMETER, Sensor.TYPE_PRESSURE, "barometer", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.HUMIDITY, Sensor.TYPE_RELATIVE_HUMIDITY, "humidity", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE, "ambient_temperature", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.MAGNETOMETER, Sensor.TYPE_MAGNETIC_FIELD, "magnetometer", nowWall, nowMono, fp)
        out += sensorObservation(HardwareObservation.SourceType.ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR, "rotation_vector", nowWall, nowMono, fp)

        out += featureObservation(
            HardwareObservation.SourceType.TOUCH,
            "touchscreen",
            PackageManager.FEATURE_TOUCHSCREEN,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.CAMERA,
            "camera",
            PackageManager.FEATURE_CAMERA_ANY,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.MICROPHONE,
            "microphone",
            PackageManager.FEATURE_MICROPHONE,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.GPS,
            "gps_gnss",
            PackageManager.FEATURE_LOCATION_GPS,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.WIFI,
            "wifi",
            PackageManager.FEATURE_WIFI,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.BLUETOOTH,
            "bluetooth",
            PackageManager.FEATURE_BLUETOOTH,
            nowWall, nowMono, fp
        )
        out += featureObservation(
            HardwareObservation.SourceType.NFC,
            "nfc",
            PackageManager.FEATURE_NFC,
            nowWall, nowMono, fp
        )

        out += biometricCapability(nowWall, nowMono, fp)
        out += keystoreCapability(nowWall, nowMono, fp)
        out += batteryObservation(nowWall, nowMono, fp)
        out += networkObservation(nowWall, nowMono, fp)
        out += cellularFeature(nowWall, nowMono, fp)

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
                permission = "NOT_APPLICABLE",
                freshness = "UNKNOWN",
                evidence = "NOT_YET_VERIFIED",
                authorization = "NOT_AUTHORIZED",
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
                permission = "NOT_APPLICABLE",
                freshness = "UNKNOWN",
                evidence = "NOT_YET_VERIFIED",
                authorization = "NOT_AUTHORIZED",
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

    private fun featureObservation(
        type: HardwareObservation.SourceType,
        name: String,
        feature: String,
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val present = pm.hasSystemFeature(feature)
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = type,
            sourceName = name,
            capabilityState = if (present) {
                HardwareObservation.CapabilityState.PRESENT
            } else {
                HardwareObservation.CapabilityState.ABSENT
            },
            bindingState = HardwareObservation.BindingState.UNBOUND,
            valueSummary = if (present) "PRESENT" else "ABSENT",
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            permission = "NOT_APPLICABLE",
            freshness = "UNKNOWN",
            evidence = "NOT_YET_VERIFIED",
            authorization = "NOT_AUTHORIZED",
            detail = mapOf("feature" to feature)
        )
    }

    private fun cellularFeature(nowWall: Long, nowMono: Long, fp: String): HardwareObservation {
        val present = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.CELLULAR,
            sourceName = "cellular",
            capabilityState = if (present) {
                HardwareObservation.CapabilityState.PRESENT
            } else {
                HardwareObservation.CapabilityState.ABSENT
            },
            bindingState = HardwareObservation.BindingState.UNBOUND,
            valueSummary = if (present) "TELEPHONY_FEATURE_PRESENT" else "ABSENT",
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            authorization = "NOT_AUTHORIZED",
            detail = mapOf("feature" to PackageManager.FEATURE_TELEPHONY)
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
            authorization = "NOT_AUTHORIZED",
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
            authorization = "NOT_AUTHORIZED",
            detail = mapOf(
                "note" to "Keystore API present; hardware-backed status requires key-specific probe"
            )
        )
    }

    private fun batteryObservation(
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (intent == null) {
            return HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = HardwareObservation.SourceType.BATTERY,
                sourceName = "battery",
                capabilityState = HardwareObservation.CapabilityState.UNKNOWN,
                bindingState = HardwareObservation.BindingState.UNKNOWN,
                valueSummary = "BATTERY_INTENT_NULL",
                monotonicTimestampNs = nowMono,
                wallClockEpochMs = nowWall,
                deviceFingerprint = fp,
                authorization = "NOT_AUTHORIZED"
            )
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.BATTERY,
            sourceName = "battery",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.BOUND,
            valueSummary = "pct=$pct charging=$charging temp_tenths_c=$temp voltage_mv=$voltage",
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            unit = "percent|mV|0.1C",
            permission = "NOT_APPLICABLE",
            freshness = "LIVE",
            evidence = "NOT_YET_VERIFIED",
            authorization = "NOT_AUTHORIZED",
            detail = mapOf(
                "level" to level.toString(),
                "scale" to scale.toString(),
                "status" to status.toString(),
                "plugged" to plugged.toString(),
                "temperature_tenths_c" to temp.toString(),
                "voltage_mv" to voltage.toString(),
                "note" to "BatteryManager extras are system-reported; not external DMM measurements"
            )
        )
    }

    private fun networkObservation(
        nowWall: Long,
        nowMono: Long,
        fp: String
    ): HardwareObservation {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val summary = if (caps == null) {
            "NO_ACTIVE_NETWORK"
        } else {
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            "wifi=$wifi cellular=$cell ethernet=$eth"
        }
        return HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.WIFI,
            sourceName = "network_active",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = if (caps != null) {
                HardwareObservation.BindingState.BOUND
            } else {
                HardwareObservation.BindingState.UNBOUND
            },
            valueSummary = summary,
            monotonicTimestampNs = nowMono,
            wallClockEpochMs = nowWall,
            deviceFingerprint = fp,
            freshness = "LIVE",
            evidence = "NOT_YET_VERIFIED",
            authorization = "NOT_AUTHORIZED",
            detail = mapOf("note" to "Connectivity snapshot only; not a permission grant")
        )
    }
}
