package com.trustsmotion.swiandroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class AssistantActivity : AppCompatActivity(),
    TextToSpeech.OnInitListener {

    private lateinit var transcript: EditText
    private lateinit var status: TextView

    private var tts: TextToSpeech? = null
    private var language = "en-ZA"
    private var persona = "JOE"

    private val micPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) listen()
            else status.text =
                "MICROPHONE_PERMISSION=DENIED"
        }

    private val speechResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {
                val words = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )?.firstOrNull()

                if (!words.isNullOrBlank()) {
                    transcript.setText(words)
                    status.text = "TRANSCRIPTION=AVAILABLE_FOR_REVIEW"
                }
            } else {
                status.text = "TRANSCRIPTION=NOT_COMPLETED"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        buildScreen()
        tts = TextToSpeech(this, this)
        requestNotificationPermission()
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 32)
        }

        root.addView(TextView(this).apply {
            text = "SWI | JOE & ALITA"
            textSize = 26f
        })

        root.addView(TextView(this).apply {
            text = """
                Structured Workflow Intelligence

                Ronald Mosidila
                Founder, Trusts Motion
                Botswana

                Source identity must be verified against designated
                SWI repositories, not acronym similarity.
            """.trimIndent()
        })

        root.addView(Button(this).apply {
            text = "PERSONA: JOE / SWITCH TO ALITA"
            setOnClickListener {
                persona =
                    if (persona == "JOE") "ALITA" else "JOE"
                text = "PERSONA: $persona"
                status.text = "PERSONA_SELECTED=$persona"
            }
        })

        root.addView(Button(this).apply {
            text = "LANGUAGE: ENGLISH / SETSWANA / SESOTHO"
            setOnClickListener {
                language = when (language) {
                    "en-ZA" -> "tn-BW"
                    "tn-BW" -> "st-ZA"
                    else -> "en-ZA"
                }

                text = "LANGUAGE: $language"
                updateVoice()
            }
        })

        transcript = EditText(this).apply {
            hint = "Ask JOE or ALITA about SWI..."
            minLines = 3
        }

        root.addView(transcript)

        root.addView(Button(this).apply {
            text = "MICROPHONE / TRANSCRIBE"
            setOnClickListener {
                if (
                    ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    listen()
                } else {
                    micPermission.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }
        })

        root.addView(Button(this).apply {
            text = "ASK JOE / ALITA"
            setOnClickListener {
                if (transcript.text.isNullOrBlank()) {
                    status.text = "HOLD: QUESTION REQUIRED"
                } else {
                    status.text = """
                        PERSONA=$persona
                        SWI_API_CONNECTION=UNKNOWN
                        LLM_PROVIDER_CONNECTION=NOT_CONFIGURED
                        REPOSITORY_RETRIEVAL=NOT_CONNECTED

                        ANSWER=BLOCKED
                        Configure and verify the SWI gateway first.

                        EXECUTION_PERMITTED=NO
                    """.trimIndent()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "GOOGLE SEARCH: SWI"
            setOnClickListener {
                val question = transcript.text.toString()

                val query =
                    "\"Structured Workflow Intelligence\" " +
                    "\"Ronald Mosidila\" Trusts Motion $question"

                openUrl(
                    "https://www.google.com/search?q=" +
                        Uri.encode(query)
                )
            }
        })

        root.addView(Button(this).apply {
            text = "CHECK APP UPDATE"
            setOnClickListener { checkUpdate() }
        })

        root.addView(Button(this).apply {
            text = "SPEAK STATUS"
            setOnClickListener {
                tts?.speak(
                    status.text.toString(),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "swi-status"
                )
            }
        })

        status = TextView(this).apply {
            textSize = 14f
            text = """
                SWI_API_CONNECTION=UNKNOWN
                LLM_PROVIDER_CONNECTION=NOT_CONFIGURED
                MICROPHONE=NOT_REQUESTED
                TRANSCRIPTION=NOT_STARTED
                EXECUTION_PERMITTED=NO
            """.trimIndent()
        }

        root.addView(status)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun listen() {
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                language
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak your question. Review before submission."
            )
        }

        try {
            speechResult.launch(intent)
            status.text = "TRANSCRIPTION=LISTENING"
        } catch (e: Exception) {
            status.text =
                "TRANSCRIPTION_UNAVAILABLE=${e.message}"
        }
    }

    private fun updateVoice() {
        val locale = when (language) {
            "tn-BW" -> Locale("tn", "BW")
            "st-ZA" -> Locale("st", "ZA")
            else -> Locale("en", "ZA")
        }

        val result = tts?.setLanguage(locale)

        status.text = when {
            result == null ->
                "VOICE_ENGINE=INITIALIZING"

            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED ->
                "VOICE_LOCALE=$language\nVOICE_SUPPORT=NOT_AVAILABLE"

            else ->
                "VOICE_LOCALE=$language\nVOICE_ENGINE_SUPPORT=REPORTED"
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            updateVoice()
        } else {
            status.text = "TTS_INITIALIZATION_FAILED"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "swi_updates",
                "SWI Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                8301
            )
        }
    }

    private fun checkUpdate() {
        status.text = "UPDATE_CHECK=IN_PROGRESS"

        Thread {
            try {
                val connection = java.net.URL(
                    "https://api.github.com/repos/" +
                    "Kelronmos/SWI-Android-Prototype/releases/latest"
                ).openConnection() as java.net.HttpURLConnection

                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty(
                    "User-Agent",
                    "SWI-Android"
                )

                val code = connection.responseCode

                val body = (
                    if (code in 200..299)
                        connection.inputStream
                    else connection.errorStream
                ).bufferedReader().use { it.readText() }

                connection.disconnect()

                val json = org.json.JSONObject(body)
                val tag = json.optString("tag_name")
                val url = json.optString("html_url")

                runOnUiThread {
                    if (code in 200..299 && tag.isNotBlank()) {
                        AlertDialog.Builder(this)
                            .setTitle("SWI update available")
                            .setMessage(
                                "Release: $tag\nReview before installing?"
                            )
                            .setNegativeButton("LATER", null)
                            .setPositiveButton("VIEW") { _, _ ->
                                openUrl(url)
                            }
                            .show()

                        status.text =
                            "UPDATE_CHECK=COMPLETED\nLATEST_RELEASE=$tag"
                    } else {
                        status.text =
                            "UPDATE_CHECK=UNKNOWN\nHTTP=$code"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text =
                        "UPDATE_CHECK=UNKNOWN\n${e.message}"
                }
            }
        }.start()
    }

    private fun openUrl(url: String) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        )
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
