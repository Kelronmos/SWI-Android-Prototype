package com.trustsmotion.swiandroid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.trustsmotion.swiandroid.recovery.HumanAuthorizationState
import com.trustsmotion.swiandroid.recovery.LedgerIntegrity
import com.trustsmotion.swiandroid.recovery.RecoveryAttempt
import com.trustsmotion.swiandroid.recovery.RecoveryItemIdentity
import com.trustsmotion.swiandroid.recovery.RecoveryLedger
import com.trustsmotion.swiandroid.recovery.RecoveryState
import com.trustsmotion.swiandroid.recovery.VerificationResult
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** User-directed, read-only recovery evidence capture. It never restores or overwrites a file. */
class RecoverySuiteActivity : Activity() {
    private val ledger by lazy { RecoveryLedger(getFileStreamPath("swi_recovery_ledger.v1")) }
    private lateinit var keyInput: EditText
    private lateinit var itemInput: EditText
    private lateinit var status: TextView
    private var pickedOriginal: Uri? = null
    private var pickedCandidate: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        root.addView(TextView(this).apply {
            text = "SWI RECOVERY SUITE\nDETECTED ≠ RECOVERED ≠ VERIFIED\nKEY EXISTS ≠ FILE EXISTS"
            textSize = 21f
        })
        root.addView(TextView(this).apply {
            text = "Read-only, user-selected document checks. No background scanning, file writes, deletion, replacement, or SWI authorization."
        })
        keyInput = EditText(this).apply { hint = "Existing SWI key (required)" }
        itemInput = EditText(this).apply { hint = "Stable item identity (required)" }
        root.addView(keyInput)
        root.addView(itemInput)
        root.addView(button("SELECT ORIGINAL FILE AND RECORD IDENTITY") { selectDocument(REQUEST_ORIGINAL) })
        root.addView(button("CHECK RECORDED ORIGINAL") { checkOriginal() })
        root.addView(button("SELECT CANDIDATE IN AN AUTHORIZED LOCATION") { selectDocument(REQUEST_CANDIDATE) })
        root.addView(button("VERIFY SELECTED CANDIDATE") { verifyCandidate() })
        root.addView(button("READ LEDGER STATUS") { showLedger() })
        status = TextView(this).apply { textSize = 14f; setPadding(4, 16, 4, 8) }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(root) })
        showLedger()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun selectDocument(request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, request)
    }

    @Deprecated("Activity Result API migration is tracked separately")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            status.text = "UNKNOWN: document selection was cancelled or unavailable. Existing identity records remain unchanged."
            return
        }
        val uri = data?.data ?: run { status.text = "HOLD: selected document URI missing"; return }
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The current transient grant can still be used in this Activity; persistence is UNKNOWN.
        } catch (_: IllegalArgumentException) {
            // Some document providers do not offer persistable grants.
        }
        if (requestCode == REQUEST_ORIGINAL) {
            pickedOriginal = uri
            registerOriginal(uri)
        } else if (requestCode == REQUEST_CANDIDATE) {
            pickedCandidate = uri
            status.text = "Candidate selected from a user-authorized URI. It is not recovered or verified until checked against the preserved identity and hash."
        }
    }

    private fun registerOriginal(uri: Uri) {
        val key = keyInput.text.toString().trim()
        val itemId = itemInput.text.toString().trim()
        if (key.isBlank() || itemId.isBlank()) {
            status.text = "HOLD: enter the existing SWI key and stable item identity before recording. No key is generated here."
            return
        }
        val digest = try { hash(uri) } catch (error: SecurityException) {
            append(key, itemId, uri.toString(), null, RecoveryState.ACCESS_DENIED,
                listOf(uri.toString()), listOf("USER_SELECTED_ORIGINAL", "READ_DENIED"), null, null,
                VerificationResult.UNKNOWN, "The provider denied read access; file existence is not established.")
            return
        } catch (error: Exception) {
            append(key, itemId, uri.toString(), null, RecoveryState.SOURCE_UNAVAILABLE_UNKNOWN,
                listOf(uri.toString()), listOf("USER_SELECTED_ORIGINAL", "READ_FAILED"), null, null,
                VerificationResult.UNKNOWN, "The provider could not supply bytes (${error.javaClass.simpleName}); deletion, movement, disconnection, and corruption are not distinguished.")
            return
        }
        append(key, itemId, uri.toString(), digest, RecoveryState.SOURCE_REGISTERED,
            listOf(uri.toString()), listOf("USER_SELECTED_ORIGINAL", "SHA256_RECORDED"), uri.toString(), digest,
            VerificationResult.MATCH, "Original bytes were readable at detection time; origin and SWI authority are not proven.")
    }

    private fun checkOriginal() {
        val previous = latestIdentity() ?: return
        val uri = runCatching { Uri.parse(previous.item.lastKnownLocation) }.getOrNull()
        if (uri == null) {
            status.text = "UNKNOWN: no recorded source URI. The SWI identity is retained in the ledger."
            return
        }
        try {
            val actual = hash(uri)
            val match = previous.item.expectedSha256 != null && actual == previous.item.expectedSha256
            val expected = previous.item.expectedSha256
            append(previous.item.swiKey, previous.item.itemId, previous.item.lastKnownLocation,
                expected, if (expected == null) RecoveryState.INTEGRITY_UNKNOWN else if (match) RecoveryState.SOURCE_REGISTERED else RecoveryState.INTEGRITY_MISMATCH,
                listOf(uri.toString()), listOf("CHECK_RECORDED_ORIGINAL", "SHA256_COMPARISON"), uri.toString(), actual,
                if (expected == null) VerificationResult.UNKNOWN else if (match) VerificationResult.MATCH else VerificationResult.MISMATCH,
                if (expected == null) "No baseline digest exists; equality cannot be established." else if (match) "Recorded source remains readable and matches the stored digest." else "Bytes differ from the recorded digest; content is not replaced.")
        } catch (error: SecurityException) {
            appendFrom(previous, RecoveryState.ACCESS_DENIED, listOf(uri.toString()), "READ_DENIED",
                "Access denied. This does not prove deletion.")
        } catch (error: FileNotFoundException) {
            appendFrom(previous, RecoveryState.SOURCE_UNAVAILABLE_UNKNOWN, listOf(uri.toString()), "SOURCE_UNAVAILABLE",
                "Provider reports the URI unavailable. Movement, deletion, disconnection, and provider failure remain unresolved.")
        } catch (error: Exception) {
            appendFrom(previous, RecoveryState.STORAGE_UNAVAILABLE, listOf(uri.toString()), "READ_FAILED",
                "Storage/provider read failed (${error.javaClass.simpleName}); corruption and disconnection are not distinguished.")
        }
    }

    private fun verifyCandidate() {
        val previous = latestIdentity() ?: return
        val uri = pickedCandidate
        if (uri == null) {
            status.text = "HOLD: select a candidate from an authorized location first."
            return
        }
        try {
            val actual = hash(uri)
            val expected = previous.item.expectedSha256
            val match = expected != null && actual == expected
            append(previous.item.swiKey, previous.item.itemId, previous.item.lastKnownLocation, expected,
                if (expected == null) RecoveryState.CANDIDATE_INTEGRITY_UNKNOWN else if (match) RecoveryState.CANDIDATE_INTEGRITY_MATCH else RecoveryState.CANDIDATE_INTEGRITY_MISMATCH,
                listOf(uri.toString()), listOf("USER_SELECTED_CANDIDATE", "SHA256_COMPARISON"), uri.toString(), actual,
                if (match) VerificationResult.MATCH else if (expected == null) VerificationResult.UNKNOWN else VerificationResult.MISMATCH,
                if (match) "Candidate bytes match the stored digest. Candidate is not restored; human authority and source provenance remain unverified." else "Candidate does not match the stored digest, or no baseline digest exists; not recovered or verified.")
        } catch (error: SecurityException) {
            appendFrom(previous, RecoveryState.ACCESS_DENIED, listOf(uri.toString()), "CANDIDATE_READ_DENIED",
                "Candidate location is not readable; no recovery is claimed.")
        } catch (error: Exception) {
            appendFrom(previous, RecoveryState.SOURCE_UNAVAILABLE_UNKNOWN, listOf(uri.toString()), "CANDIDATE_READ_FAILED",
                "Candidate bytes unavailable (${error.javaClass.simpleName}); recovery status remains UNKNOWN.")
        }
    }

    private fun latestIdentity(): RecoveryAttempt? {
        val key = keyInput.text.toString().trim()
        val itemId = itemInput.text.toString().trim()
        if (key.isBlank() || itemId.isBlank()) {
            status.text = "HOLD: enter the existing SWI key and item identity."
            return null
        }
        val snapshot = ledger.read()
        if (snapshot.integrity != LedgerIntegrity.VALID) {
            status.text = "HOLD: recovery ledger integrity failure. Preserve it; do not overwrite or repair automatically."
            return null
        }
        return ledger.latestFor(key, itemId).also {
            if (it == null) status.text = "UNKNOWN: no identity record for this key/item. Register the original first; do not infer deletion."
        }
    }

    private fun appendFrom(previous: RecoveryAttempt, state: RecoveryState, locations: List<String>, action: String, uncertainty: String) =
        append(previous.item.swiKey, previous.item.itemId, previous.item.lastKnownLocation,
            previous.item.expectedSha256, state, locations, listOf(action), null, null,
            VerificationResult.UNKNOWN, uncertainty)

    private fun append(
        key: String, itemId: String, original: String, expected: String?, state: RecoveryState,
        locations: List<String>, actions: List<String>, source: String?, actualHash: String?,
        verification: VerificationResult, uncertainty: String
    ) {
        val snapshot = ledger.read()
        if (snapshot.integrity != LedgerIntegrity.VALID) {
            status.text = "HOLD: ledger integrity failure. No records were changed."
            return
        }
        val attempt = RecoveryAttempt(
            attemptId = UUID.randomUUID().toString(),
            item = RecoveryItemIdentity(key, itemId, original, expected),
            detectedAt = timestamp(), observedState = state,
            searchLocations = locations, recoveryActions = actions,
            observedContentSource = source, observedContentSha256 = actualHash,
            verification = verification, uncertainty = uncertainty,
            humanAuthorization = HumanAuthorizationState.REQUIRED,
            approvalRecordId = null
        )
        try {
            ledger.append(attempt)
            status.text = "RECOVERY ATTEMPT RECORDED\nKEY_EXISTS=${key.take(72)}\nITEM_ID=${itemId.take(120)}\nSTATE=${state.name}\nVERIFICATION=${verification.name}\nRECOVERED=NO\nHUMAN_AUTHORIZATION=REQUIRED\nPROVEN=NO SEALED=NO PRODUCTION_AUTHORIZED=NO\n$uncertainty"
        } catch (error: Exception) {
            status.text = "HOLD: ledger write failed (${error.javaClass.simpleName}). Existing journal preserved; identity not deleted."
        }
    }

    private fun showLedger() {
        val snapshot = ledger.read()
        status.text = if (snapshot.integrity == LedgerIntegrity.VALID) {
            "LEDGER_INTEGRITY=VALID (local hash chain only)\nATTEMPTS=${snapshot.attempts.size}\nIDENTITIES_RETAINED=${snapshot.attempts.map { it.item.swiKey to it.item.itemId }.distinct().size}\nRECOVERED=NO unless separately established\nPROVEN=NO SEALED=NO PRODUCTION_AUTHORIZED=NO"
        } else {
            "LEDGER_INTEGRITY=CORRUPT_OR_TAMPERED\nVERIFIED_PREFIX_ATTEMPTS=${snapshot.attempts.size}\nIDENTITIES_IN_VERIFIED_PREFIX=${snapshot.attempts.map { it.item.swiKey to it.item.itemId }.distinct().size}\nHOLD: journal preserved; automatic repair disabled."
        }
    }

    private fun hash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: throw FileNotFoundException("Provider returned no input stream")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private companion object {
        const val REQUEST_ORIGINAL = 11
        const val REQUEST_CANDIDATE = 12
    }
}
