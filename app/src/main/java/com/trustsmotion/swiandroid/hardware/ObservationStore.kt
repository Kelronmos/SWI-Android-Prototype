package com.trustsmotion.swiandroid.hardware

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bounded in-memory history of hardware observations.
 * Export is evidence of observation, not authorization.
 */
class ObservationStore(private val capacity: Int = 500) {

    private val lock = ReentrantLock()
    private val deque = ArrayDeque<HardwareObservation>(capacity)

    fun add(observation: HardwareObservation) {
        lock.withLock {
            if (deque.size >= capacity) {
                deque.removeFirst()
            }
            deque.addLast(observation)
        }
    }

    fun snapshot(): List<HardwareObservation> = lock.withLock {
        deque.toList()
    }

    fun latestBySource(): Map<HardwareObservation.SourceType, HardwareObservation> {
        val map = linkedMapOf<HardwareObservation.SourceType, HardwareObservation>()
        for (obs in snapshot().asReversed()) {
            if (!map.containsKey(obs.sourceType)) {
                map[obs.sourceType] = obs
            }
        }
        return map
    }

    fun exportJson(deviceFingerprint: String): String {
        val root = JSONObject()
        root.put("schema_id", "swi.android.hardware_observation_export")
        root.put("schema_version", "1")
        root.put("device_fingerprint", deviceFingerprint)
        root.put("exported_at_epoch_ms", System.currentTimeMillis())
        root.put("count", snapshot().size)

        val nonClaims = JSONObject()
        nonClaims.put("observation_is_authorization", false)
        nonClaims.put("swi_authorized", false)
        nonClaims.put("production_authorized", false)
        nonClaims.put("proven", false)
        nonClaims.put("sealed", false)
        nonClaims.put("satisfies_H", false)
        nonClaims.put("permits_action", false)
        root.put("non_claims", nonClaims)

        val arr = JSONArray()
        for (obs in snapshot()) {
            val o = JSONObject()
            for ((k, v) in obs.toExportMap()) {
                when (v) {
                    is Map<*, *> -> {
                        val nested = JSONObject()
                        for ((nk, nv) in v) {
                            nested.put(nk.toString(), nv)
                        }
                        o.put(k, nested)
                    }
                    else -> o.put(k, v)
                }
            }
            arr.put(o)
        }
        root.put("observations", arr)
        return root.toString(2)
    }

    fun clear() = lock.withLock { deque.clear() }
}
