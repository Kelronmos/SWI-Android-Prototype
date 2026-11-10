package com.trustsmotion.swiandroid

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    private val termsVersion = "SWI-ANDROID-TERMS-0.1"
    private var page = 0
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var content: TextView
    private lateinit var next: Button
    private lateinit var acceptance: CheckBox

    private val headings = listOf(
        "WELCOME TO SWI",
        "WHAT IS SWI?",
        "HOW THE WORKFLOW WORKS",
        "YOUR DEVICE AND YOUR AUTHORITY",
        "TERMS AND CONDITIONS"
    )

    private val descriptions = listOf(
        "Structured Workflow Intelligence\n\nTrusts Motion\n\nEvidence before authority. Authority before action.",
        "SWI is designed to govern structured workflows by examining evidence, scope, authority and permission before an action is allowed.\n\nIts purpose is to make decisions inspectable, controlled and accountable.",
        "INPUT → EVIDENCE → ADMISSION → AUTHORITY → AUTHORIZATION → ACTION\n\nEach stage has its own requirements. Passing one stage does not automatically pass the next.\n\nUNKNOWN is not treated as FALSE. Missing evidence must not be invented.",
        "Fingerprint authentication can verify a user through Android's biometric system and unlock a protected cryptographic operation.\n\nIt does not independently authorize payments, rewards, access or workflow execution.\n\nYour biometric data remains under Android's biometric system. This prototype does not collect fingerprint images or templates.",
        "SWI ANDROID PROTOTYPE — TERMS VERSION 0.1\n\n1. PROTOTYPE STATUS\nThis application is an experimental prototype. It is not a certified, sealed or production-authorized SWI system.\n\n2. AUTHENTICATION\nBiometric authentication is used to authorize a local cryptographic signing operation. Authentication alone does not grant permission to execute an action.\n\n3. DATA AND PRIVACY\nThe prototype creates a local cryptographic proof using Android Keystore. Do not enter passwords, financial secrets or sensitive personal information into test fields. No claim is made that a complete backend privacy or data-processing service is connected.\n\n4. WORKFLOW AUTHORITY\nA workflow identifier and action description are not proof of permission. No action should be treated as authorized merely because a fingerprint check succeeds.\n\n5. COSTS AND REWARDS\nNo payment, subscription, reward redemption or charge is authorized by this prototype. Any future paid service or rewards programme must disclose its terms and obtain the required explicit user consent separately.\n\n6. USER CONTROL\nYou may stop using the application. Uninstalling it removes the app from your device, but does not necessarily remove information held by any future connected service.\n\n7. LIMITATIONS\nThis prototype has not established production security, independent verification, backend authorization or regulatory compliance.\n\n8. CHANGES\nTerms for future versions may change. Material changes should be presented for review and acceptance before the affected service is used.\n\nBy selecting the acceptance checkbox and continuing, you acknowledge these prototype terms and limitations. This local acceptance is not proof of identity or authorization."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("swi_onboarding", MODE_PRIVATE)
        if (prefs.getBoolean("completed", false) &&
            prefs.getString("terms_version", "") == termsVersion) {
            openApp()
            return
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 32)
            setBackgroundColor(Color.rgb(18, 20, 25))
        }

        val brand = TextView(this).apply {
            text = "TRUSTS MOTION  •  SWI"
            textSize = 13f
            setTextColor(Color.rgb(242, 140, 40))
        }

        title = TextView(this).apply {
            textSize = 27f
            setTextColor(Color.rgb(242, 140, 40))
            setPadding(0, 28, 0, 18)
        }

        content = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setLineSpacing(8f, 1f)
            setPadding(0, 8, 0, 20)
        }

        acceptance = CheckBox(this).apply {
            text = "I have read and accept the SWI Android prototype terms."
            textSize = 14f
            setTextColor(Color.WHITE)
            visibility = View.GONE
            buttonTintList = android.content.res.ColorStateList.valueOf(
                Color.rgb(242, 140, 40)
            )
        }

        next = Button(this).apply {
            text = "CONTINUE"
            setOnClickListener { advance() }
        }

        root.addView(brand)
        root.addView(title)

        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        root.addView(acceptance)
        root.addView(next)

        setContentView(root)
        renderPage()
    }

    private fun renderPage() {
        title.text = headings[page]
        content.text = descriptions[page]

        acceptance.visibility =
            if (page == headings.lastIndex) View.VISIBLE else View.GONE

        next.text = if (page == headings.lastIndex) {
            "ACCEPT AND OPEN SWI"
        } else {
            "CONTINUE"
        }

        next.isEnabled = page != headings.lastIndex || acceptance.isChecked
        acceptance.setOnCheckedChangeListener { _, checked ->
            next.isEnabled = checked
        }

        val animation = AlphaAnimation(0f, 1f).apply {
            duration = 350
        }
        title.startAnimation(animation)
        content.startAnimation(animation)
    }

    private fun advance() {
        if (page < headings.lastIndex) {
            page++
            renderPage()
            return
        }

        if (!acceptance.isChecked) return

        getSharedPreferences("swi_onboarding", MODE_PRIVATE)
            .edit()
            .putBoolean("completed", true)
            .putString("terms_version", termsVersion)
            .putLong("accepted_at_local_device_ms", System.currentTimeMillis())
            .apply()

        openApp()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
