package com.trustsmotion.swiandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.trustsmotion.swiandroid.hardware.HardwareInventory
import com.trustsmotion.swiandroid.hardware.HardwareObservation
import com.trustsmotion.swiandroid.hardware.ObservationStore
import com.trustsmotion.swiandroid.hardware.SensorBinder
import com.trustsmotion.swiandroid.voice.GuidedBreathingSession
import com.trustsmotion.swiandroid.voice.VoiceVerificationModule
import java.util.UUID

/**
 * Live hardware + voice/breathing dashboard.
 *
 * Observations only. Does not authorize actions.
 * PROVEN=NO SEALED=NO PRODUCTION_AUTHORIZED=NO
 */
class HardwareDashboardActivity : AppCompatActivity() {

    private val orange = Color.rgb(242, 140, 40)
    private val black = Color.rgb(18, 20, 25)
    private val blue = Color.rgb(40, 120, 200)
    private val white = Color.WHITE

    private lateinit var inventory: HardwareInventory
    private lateinit var store: ObservationStore
    private lateinit var binder: SensorBinder
    private lateinit var voice: VoiceVerificationModule
    private lateinit var breathing: GuidedBreathingSession
    private lateinit var statusBoard: TextView
    private lateinit var historyView: TextView
    private lateinit var touchPad: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val staleChecker = object : Runnable {
        override fun run() {
            if (::binder.isInitialized) {
                binder.checkStale()
            }
            refreshBoard()
            handler.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inventory = HardwareInventory(this)
        store = ObservationStore()
        val fp = inventory.deviceFingerprint()

        binder = SensorBinder(this, fp) { obs ->
            store.add(obs)
            runOnUiThread { refreshBoard() }
        }

        voice = VoiceVerificationModule(this, fp) { obs ->
            store.add(obs)
            runOnUiThread { refreshBoard() }
        }
        voice.init()

        breathing = GuidedBreathingSession(
            deviceFingerprint = fp,
            speak = { line -> voice.speak(line) },
            onObservation = { obs ->
                store.add(obs)
                runOnUiThread { refreshBoard() }
            }
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(28, 36, 28, 28)
        }

        val title = TextView(this).apply {
            text = "SWI HARDWARE"
            textSize = 26f
            setTextColor(orange)
        }
        val subtitle = TextView(this).apply {
            text = "OBSERVATION ONLY  ·  NOT AUTHORIZATION"
            textSize = 12f
            setTextColor(blue)
            setPadding(0, 4, 0, 16)
        }

        statusBoard = TextView(this).apply {
            textSize = 12f
            setTextColor(white)
            setTextIsSelectable(true)
        }

        touchPad = TextView(this).apply {
            text = "TOUCH PAD\nTouch here — events are observations only"
            textSize = 14f
            setTextColor(white)
            setBackgroundColor(Color.rgb(30, 34, 42))
            setPadding(24, 40, 24, 40)
            gravity = Gravity.CENTER
            setOnTouchListener { _, event ->
                recordTouch(event, fp)
                true
            }
        }

        historyView = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(200, 200, 200))
            setTextIsSelectable(true)
        }

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(Button(this).apply {
            text = "DISCOVER"
            setOnClickListener { runDiscovery() }
        })
        btnRow.addView(Button(this).apply {
            text = "EXPORT"
            setOnClickListener { exportEvidence() }
        })
        btnRow.addView(Button(this).apply {
            text = "CLEAR"
            setOnClickListener {
                store.clear()
                refreshBoard()
            }
        })

        val voiceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        voiceRow.addView(Button(this).apply {
            text = "SPEAK VERIFY"
            setOnClickListener { voice.speak() }
        })
        voiceRow.addView(Button(this).apply {
            text = "STOP TTS"
            setOnClickListener { voice.pause() }
        })
        voiceRow.addView(Button(this).apply {
            text = "BREATH START"
            setOnClickListener { breathing.start() }
        })
        voiceRow.addView(Button(this).apply {
            text = "BREATH STOP"
            setOnClickListener { breathing.stop() }
        })

        root.addView(title)
        root.addView(subtitle)
        root.addView(btnRow)
        root.addView(voiceRow)
        root.addView(touchPad)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusBoard)
            addView(historyView)
        }
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        runDiscovery()
        binder.start()
        handler.post(staleChecker)
    }

    private fun runDiscovery() {
        for (obs in inventory.discover()) {
            store.add(obs)
        }
        refreshBoard()
    }

    private fun recordTouch(event: MotionEvent, fp: String) {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "OTHER_${event.actionMasked}"
        }
        if (action == "MOVE" && event.eventTime % 40 > 5) return

        val obs = HardwareObservation(
            observationId = UUID.randomUUID().toString(),
            sourceType = HardwareObservation.SourceType.TOUCH,
            sourceName = "touchscreen",
            capabilityState = HardwareObservation.CapabilityState.PRESENT,
            bindingState = HardwareObservation.BindingState.BOUND,
            valueSummary = "$action x=${event.x} y=${event.y} p=${event.pressure}",
            monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
            wallClockEpochMs = System.currentTimeMillis(),
            deviceFingerprint = fp,
            freshness = "LIVE",
            evidence = "NOT_YET_VERIFIED",
            authorization = "NOT_AUTHORIZED",
            detail = mapOf(
                "pointer_count" to event.pointerCount.toString(),
                "action" to action
            )
        )
        store.add(obs)
        refreshBoard()
    }

    private fun refreshBoard() {
        val latest = store.latestBySource()
        val sb = StringBuilder()
        sb.append("DEVICE: ${inventory.deviceFingerprint()}\n")
        sb.append("LISTENING: ${binder.isListening()}\n")
        sb.append("HISTORY: ${store.snapshot().size}\n\n")
        sb.append("— CAPABILITY / BINDING —\n")
        for (type in HardwareObservation.SourceType.values()) {
            val obs = latest[type]
            if (obs == null) {
                sb.append("${type.name}: UNKNOWN (no observation)\n")
            } else {
                val bind = obs.effectiveBinding(System.currentTimeMillis())
                sb.append(
                    "${type.name}: cap=${obs.capabilityState} bind=$bind auth=${obs.authorization}\n  ${obs.valueSummary}\n"
                )
            }
        }
        sb.append(
            "\nNON-CLAIMS: observation≠authorization; voice_match≠identity; " +
                "proven=false; sealed=false; production_authorized=false\n"
        )
        statusBoard.text = sb.toString()

        val hist = store.snapshot().takeLast(14).asReversed()
        val hb = StringBuilder("— RECENT —\n")
        for (h in hist) {
            hb.append("[${h.sourceType}] ${h.bindingState} ${h.valueSummary}\n")
        }
        historyView.text = hb.toString()
    }

    private fun exportEvidence() {
        val json = store.exportJson(inventory.deviceFingerprint())
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("swi_hardware_observations", json))
        Toast.makeText(
            this,
            "Exported ${store.snapshot().size} observations to clipboard (observation only)",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onPause() {
        super.onPause()
        binder.stop()
        breathing.pause()
        voice.pause()
        store.add(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = HardwareObservation.SourceType.SYSTEM,
                sourceName = "lifecycle",
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.HOLD,
                valueSummary = "APP_PAUSED sensors_unregistered",
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = inventory.deviceFingerprint(),
                authorization = "NOT_AUTHORIZED"
            )
        )
    }

    override fun onResume() {
        super.onResume()
        if (!binder.isListening()) {
            binder.start()
        }
        store.add(
            HardwareObservation(
                observationId = UUID.randomUUID().toString(),
                sourceType = HardwareObservation.SourceType.SYSTEM,
                sourceName = "lifecycle",
                capabilityState = HardwareObservation.CapabilityState.PRESENT,
                bindingState = HardwareObservation.BindingState.BOUND,
                valueSummary = "APP_RESUMED sensors_registered",
                monotonicTimestampNs = SystemClock.elapsedRealtimeNanos(),
                wallClockEpochMs = System.currentTimeMillis(),
                deviceFingerprint = inventory.deviceFingerprint(),
                authorization = "NOT_AUTHORIZED"
            )
        )
        refreshBoard()
    }

    override fun onDestroy() {
        handler.removeCallbacks(staleChecker)
        binder.stop()
        breathing.stop()
        voice.shutdown()
        super.onDestroy()
    }
}
