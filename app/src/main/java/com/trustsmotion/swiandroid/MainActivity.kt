package com.trustsmotion.swiandroid

import android.content.Intent
import android.net.Uri

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val alias = "SWI_ANDROID_AUTHORITY_KEY_V1"
    private lateinit var status: TextView
    private lateinit var workflow: EditText
    private lateinit var action: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = "SWI ANDROID"
            textSize = 28f
            setTextColor(0xFFF28C28.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "Structured Workflow Intelligence"
            textSize = 16f
        }

        workflow = EditText(this).apply {
            hint = "Workflow identifier"
            setSingleLine(true)
        }

        action = EditText(this).apply {
            hint = "Exact action requested"
            setSingleLine(true)
        }

        val button = Button(this).apply {
            text = "AUTHENTICATE WITH FINGERPRINT"
            setOnClickListener { authenticate() }
        }

        status = TextView(this).apply {
            textSize = 14f
            text = "BIOMETRIC STATUS: NOT CHECKED"
        }

        val hardwareBtn = Button(this).apply {
            text = "OPEN HARDWARE DASHBOARD"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HardwareDashboardActivity::class.java))
            }
        }

        layout.addView(heading)
        layout.addView(subtitle)
        layout.addView(workflow)
        layout.addView(action)
        layout.addView(button)
        layout.addView(status)
        layout.addView(hardwareBtn)

        addLinkButton(
            layout,
            "OPEN SWI WEB APP",
            "https://swi.trustsmotion.com"
        )

        addLinkButton(
            layout,
            "TRUSTS MOTION WEBSITE",
            "https://www.trustsmotion.com"
        )

        addLinkButton(
            layout,
            "SWI ANDROID REPOSITORY",
            "https://github.com/Kelronmos/SWI-Android-Prototype"
        )

        setContentView(layout)
        checkBiometrics()
    }


    private fun addLinkButton(
        layout: LinearLayout,
        label: String,
        url: String
    ) {
        val button = Button(this).apply {
            text = label
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        layout.addView(button)
    }

    private fun checkBiometrics() {
        val manager = BiometricManager.from(this)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )

        status.text = when (result) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "BIOMETRIC_STRONG: AVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "NO STRONG BIOMETRIC ENROLLED"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "BIOMETRIC HARDWARE UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "BIOMETRIC HARDWARE TEMPORARILY UNAVAILABLE"
            else -> "BIOMETRIC STATUS UNKNOWN: $result"
        }
    }

    private fun createKey() {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)

        if (store.containsAlias(alias)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        generator.initialize(specification)
        generator.generateKeyPair()
    }

    private fun authenticate() {
        val workflowId = workflow.text.toString().trim()
        val actionId = action.text.toString().trim()

        if (workflowId.isBlank() || actionId.isBlank()) {
            status.text = "HOLD: WORKFLOW AND ACTION REQUIRED"
            return
        }

        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) != BiometricManager.BIOMETRIC_SUCCESS) {
            checkBiometrics()
            return
        }

        try {
            createKey()

            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)

            val privateKey = store.getKey(alias, null) as java.security.PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)

            val random = ByteArray(32)
            SecureRandom().nextBytes(random)
            val nonce = Base64.encodeToString(
                random,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val formatter = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
            )
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val issuedAt = formatter.format(Date())

            val payload = (
                "SWI-AUTH-V1\n" +
                "workflow=${workflowId.length}:$workflowId\n" +
                "action=${actionId.length}:$actionId\n" +
                "nonce=$nonce\n" +
                "issued_at=$issuedAt"
            ).toByteArray(Charsets.UTF_8)

            val executor = ContextCompat.getMainExecutor(this)

            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)

                        try {
                            val authenticatedSignature =
                                result.cryptoObject?.signature
                                    ?: throw IllegalStateException(
                                        "Authenticated signature unavailable"
                                    )

                            authenticatedSignature.update(payload)
                            val signed = authenticatedSignature.sign()

                            val publicKey = store.getCertificate(alias).publicKey
                            val encodedSignature = Base64.encodeToString(
                                signed,
                                Base64.NO_WRAP
                            )
                            val encodedPublicKey = Base64.encodeToString(
                                publicKey.encoded,
                                Base64.NO_WRAP
                            )

                            status.text =
                                "BIOMETRIC_AUTHENTICATED\n" +
                                "LOCAL_CRYPTOGRAPHIC_PROOF_CREATED\n" +
                                "WORKFLOW: $workflowId\n" +
                                "ACTION: $actionId\n" +
                                "NONCE: $nonce\n" +
                                "SIGNATURE: $encodedSignature\n" +
                                "PUBLIC KEY: $encodedPublicKey\n" +
                                "SWI AUTHORIZATION: NOT YET VERIFIED\n" +
                                "EXECUTION: NOT AUTHORIZED"

                        } catch (error: Exception) {
                            status.text =
                                "PROOF CREATION FAILED: ${error.message}"
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        status.text = "AUTHENTICATION ERROR: $errString"
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        status.text = "BIOMETRIC NOT RECOGNIZED: RETRY"
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("SWI Human Authority")
                .setSubtitle("Authenticate this exact workflow action")
                .setDescription("Approval does not independently authorize execution.")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                .setNegativeButtonText("CANCEL")
                .build()

            prompt.authenticate(
                promptInfo,
                BiometricPrompt.CryptoObject(signature)
            )

            status.text = "WAITING FOR ANDROID BIOMETRIC AUTHENTICATION"

        } catch (error: Exception) {
            status.text = "AUTHORITY INITIALIZATION FAILED: ${error.message}"
        }
    }
}
