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
 * Uses SENSOR_DELAY_UI by default to limit battery impact.
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
        Sensor.TYPE_LIGHT to HardwareObservation.SourceType.LIGHT,
        Sensor.TYPE_PRESSURE to HardwareObservation.SourceType.BAROMETER,
        Sensor.TYPE_RELATIVE_HUMIDITY to HardwareObservation.SourceType.HUMIDITY,
        Sensor.TYPE_AMBIENT_TEMPERATURE to HardwareObservation.SourceType.TEMPERATURE,
        Sensor.TYPE_MAGNETIC_FIELD to HardwareObservation.SourceType.MAGNETOMETER,
        Sensor.TYPE_ROTATION_VECTOR to HardwareObservation.SourceType.ROTATION_VECTOR
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
                        freshness = "STALE",
                        evidence = "NOT_YET_VERIFIED",
                        authorization = "NOT_AUTHORIZED",
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

        val (summary, unit) = when (sourceType) {
            HardwareObservation.SourceType.PROXIMITY -> {
                val distance = event.values.firstOrNull() ?: Float.NaN
                val max = event.sensor.maximumRange
                val near = distance < max
                "distance=$distance near=$near max=$max" to "cm"
            }
            HardwareObservation.SourceType.ACCELEROMETER ->
                ("x=${event.values.getOrNull(0)} y=${event.values.getOrNull(1)} z=${event.values.getOrNull(2)}" to "m/s2")
            HardwareObservation.SourceType.GYROSCOPE ->
                ("x=${event.values.getOrNull(0)} y=${event.values.getOrNull(1)} z=${event.values.getOrNull(2)}" to "rad/s")
            HardwareObservation.SourceType.LIGHT ->
                ("lux=${event.values.firstOrNull()}" to "lx")
            HardwareObservation.SourceType.BAROMETER ->
                ("hpa=${event.values.firstOrNull()}" to "hPa")
            HardwareObservation.SourceType.HUMIDITY ->
                ("rh=${event.values.firstOrNull()}" to "%")
            HardwareObservation.SourceType.TEMPERATURE ->
                ("c=${event.values.firstOrNull()}" to "C")
            HardwareObservation.SourceType.MAGNETOMETER ->
                ("x=${event.values.getOrNull(0)} y=${event.values.getOrNull(1)} z=${event.values.getOrNull(2)}" to "uT")
            HardwareObservation.SourceType.ROTATION_VECTOR ->
                ("v0=${event.values.getOrNull(0)} v1=${event.values.getOrNull(1)} v2=${event.values.getOrNull(2)}" to "vector")
            else -> event.values.joinToString() to null
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
                unit = unit,
                permission = "NOT_APPLICABLE",
                freshness = "LIVE",
                evidence = "NOT_YET_VERIFIED",
                authorization = "NOT_AUTHORIZED",
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
                authorization = "NOT_AUTHORIZED",
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
                authorization = "NOT_AUTHORIZED",
                detail = mapOf("android_sensor_type" to androidType.toString())
            )
        )
    }
}
