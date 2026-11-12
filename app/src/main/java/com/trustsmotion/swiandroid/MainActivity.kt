package com.trustsmotion.swiandroid

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager

import android.os.Build
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
import androidx.core.app.NotificationCompat
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
            text = "CAMERA PRESENCE + FINGERPRINT"
            setOnClickListener { startPresenceChallenge() }
        }

        status = TextView(this).apply {
            textSize = 14f
            text = "BIOMETRIC STATUS: NOT CHECKED"
        }

        layout.addView(heading)
        layout.addView(subtitle)
        layout.addView(workflow)
        layout.addView(action)
        layout.addView(button)

        layout.addView(status)

        layout.addView(Button(this).apply {
            text = "OPEN JOE / ALITA ASSISTANT"
            setOnClickListener {
                startActivity(
                    Intent(
                        this@MainActivity,
                        AssistantActivity::class.java
                    )
                )
            }
        })

        layout.addView(Button(this).apply {
            text = "OPEN SWI RECOVERY SUITE"
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, RecoverySuiteActivity::class.java)
                )
            }
        })

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
        checkForAppVersionUpdate()
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

    /** Local post-update notice; this is not a remote push service or SWI authorization. */
    private fun checkForAppVersionUpdate() {
        val prefs = getSharedPreferences("swi_app_update_notice", MODE_PRIVATE)
        val currentVersion = packageManager.getPackageInfo(packageName, 0).longVersionCode
        val previousVersion = prefs.getLong("last_seen_version", 0L)
        if (previousVersion == 0L) {
            prefs.edit().putLong("last_seen_version", currentVersion).apply()
            return
        }
        if (currentVersion > previousVersion) {
            prefs.edit()
                .putLong("last_seen_version", currentVersion)
                .putLong("pending_notice_version", currentVersion)
                .apply()
        }
        showPendingVersionNoticeIfAllowed()
    }

    private fun showPendingVersionNoticeIfAllowed() {
        val prefs = getSharedPreferences("swi_app_update_notice", MODE_PRIVATE)
        val pendingVersion = prefs.getLong("pending_notice_version", 0L)
        if (pendingVersion == 0L) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (prefs.getLong("prompted_permission_for_version", 0L) != pendingVersion) {
                prefs.edit().putLong("prompted_permission_for_version", pendingVersion).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), UPDATE_NOTIFICATION_PERMISSION)
            } else {
                status.text = "SWI APP UPDATE NOTICE PENDING: enable notifications in Android settings to receive it."
            }
            return
        }

        val channelId = "swi_app_updates"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "SWI app updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Local notices when a newer SWI Android app version is first opened"
                enableLights(true)
                lightColor = 0xFFF28C28.toInt()
            }
            manager.createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: pendingVersion.toString()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setColor(0xFFF28C28.toInt())
            .setContentTitle("SWI Android updated")
            .setContentText("Version $versionName is installed. Prototype update does not grant SWI authority.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Version $versionName is installed. This local update notice is not a remote push, verification, seal, or authorization."
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
        prefs.edit().remove("pending_notice_version").apply()
    }

    @Deprecated("Permission callback retained for minSdk 30 compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == UPDATE_NOTIFICATION_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            showPendingVersionNoticeIfAllowed()
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

    private fun startPresenceChallenge() {
        val workflowId = workflow.text.toString().trim()
        val actionId = action.text.toString().trim()

        if (workflowId.isBlank() || actionId.isBlank()) {
            status.text = "HOLD: WORKFLOW AND ACTION REQUIRED"
            return
        }

        status.text = "CAMERA PRESENCE CHALLENGE REQUIRED"
        startActivityForResult(
            Intent(this, PresenceChallengeActivity::class.java),
            PRESENCE_REQUEST
        )
    }

    @Deprecated("Use Activity Result APIs in a future refactor")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PRESENCE_REQUEST) {
            if (resultCode == RESULT_OK) {
                status.text =
                    "PRESENCE CHALLENGE PASSED. REQUESTING BIOMETRIC AUTHENTICATION."
                authenticate()
            } else {
                status.text =
                    "HOLD: PRESENCE CHALLENGE NOT COMPLETED. NO AUTHORIZATION."
            }
        }
    }

    companion object {
        private const val PRESENCE_REQUEST = 7101
        private const val UPDATE_NOTIFICATION_PERMISSION = 7102
        private const val UPDATE_NOTIFICATION_ID = 512
    }

}
