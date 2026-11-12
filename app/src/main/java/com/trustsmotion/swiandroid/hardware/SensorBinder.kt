package com.trustsmotion.swiandroid.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds selected sensors and emits HardwareObservation records.
 * Readings are observations only — never authorization.
 */
class SensorBinder(
    context: Context,
    private val deviceFingerprint: String,
    private val onObservation: (HardwareObservation) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val lastEventMs = ConcurrentHashMap<Int, Long>()
    private var listening = false

    private val watched = listOf(
        Sensor.TYPE_PROXIMITY to HardwareObservation.SourceType.PROXIMITY,
        Sensor.TYPE_ACCELEROMETER to HardwareObservation.SourceType.ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE to HardwareObservation.SourceType.GYROSCOPE,
        Sensor.TYPE_LIGHT to HardwareObservation.SourceType.LIGHT
    )

    fun start(samplingUs: Int = SensorManager.SENSOR_DELAY_UI) {
        if (listening) return
        for ((androidType, _) in watched) {
            val sensor = sensorManager.getDefaultSensor(androidType)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, samplingUs)
            } else {
                emitAbsent(androidType)
            }
        }
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    fun isListening(): Boolean = listening

    /** Mark sensors with no recent events as STALE (call from UI timer). */
    fun checkStale(maxAgeMs: Long = 5_000L) {
        val now = System.currentTimeMillis()
        for ((androidType, sourceType) in watched) {
            val sensor = sensorManager.getDefaultSensor(androidType) ?: continue
            val last = lastEventMs[androidType] ?: continue
            if (now - last > maxAgeMs) {
                onObservation(
                    HardwareObservation(
                        observationId = UUID.randomUUID().toString(),
                        sourceType = sourceType,
                        sourceName = sensor.name ?: sourceType.name.lowercase(),
                        capabilityState = HardwareObservation.CapabilityState.PRESENT,
                        bindingState = HardwareObservation.BindingState.STALE,
                        valueSummary = "STALE age_ms=${now - last}",
                        monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                        wallClockEpochMs = now,
                        deviceFingerprint = deviceFingerprint,
                        detail = mapOf("android_sensor_type" to androidType.toString()),
                        staleAfterMs = maxAgeMs
                    )
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val sourceType = watched.firstOrNull { it.first == event.sensor.type }?.second
            ?: return
        val nowWall = System.currentTimeMillis()
        lastEventMs[event.sensor.type] = nowWall

        val summary = when (sourceType) {
            HardwareObservation.SourceType.PROXIMITY -> {
                val distance = event.values.firstOrNull() ?: Float.NaN
                val max = event.sensor.maximumRange
                val near = distance < max
                "distance=$distance near=$near max=$max"
            }
            HardwareObservation.SourceType.ACCELEROMETER ->
                "x=${event.values.getOrNull(0)} y=${event.values.getOrNull(1)} z=${event.values.getOrNull(2)}"
            HardwareObservation.SourceType.GYROSCOPE ->
                "x=${event.values.getOrNull(0)} y=${event.values.getOrNull(1)} z=${event.values.getOrNull(2)}"
            HardwareObservation.SourceType.LIGHT ->
                "lux=${event.values.firstOrNull()}"
            else -> event.values.joinToString()
        }

        onObservation(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = sourceType,
                sourceName = event.sensor.name ?: sourceType.name.lowercase(),
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.BOUND,
                valueSummary = summary,
                monotonicTimestampNs = event.timestamp,
                wallClockEpochMs = nowWall,
                deviceFingerprint = deviceFingerprint,
                detail = mapOf(
                    "android_sensor_type" to event.sensor.type.toString(),
                    "accuracy" to event.accuracy.toString()
                )
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == null) return
        val sourceType = watched.firstOrNull { it.first == sensor.type }?.second ?: return
        onObservation(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = sourceType,
                sourceName = sensor.name ?: sourceType.name.lowercase(),
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    HardwareObservation.BindingState.HOLD
                } else {
                    HardwareObservation.BindingState.BOUND
                },
                valueSummary = "accuracy=$accuracy",
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = deviceFingerprint,
                detail = mapOf("android_sensor_type" to sensor.type.toString())
            )
        )
    }

    private fun emitAbsent(androidType: Int) {
        val sourceType = watched.first { it.first == androidType }.second
        onObservation(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = sourceType,
                sourceName = sourceType.name.lowercase(),
                capabilityState = HardwareObservation.CapabilityState.ABSENT,
                bindingState = HardwareObservation.BindingState.UNBOUND,
                valueSummary = "ABSENT",
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = deviceFingerprint,
                detail = mapOf("android_sensor_type" to androidType.toString())
            )
        )
    }
}
