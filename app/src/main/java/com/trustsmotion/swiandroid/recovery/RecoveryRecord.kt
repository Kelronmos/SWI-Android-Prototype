package com.trustsmotion.swiandroid.recovery

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** States are observations. None grants permission to restore or replace content. */
enum class RecoveryState {
    SOURCE_REGISTERED,
    SOURCE_UNAVAILABLE_UNKNOWN,
    ACCESS_DENIED,
    STORAGE_UNAVAILABLE,
    INTEGRITY_MISMATCH,
    CANDIDATE_INTEGRITY_MATCH,
    CANDIDATE_INTEGRITY_MISMATCH,
    CANDIDATE_INTEGRITY_UNKNOWN,
    INTEGRITY_UNKNOWN
}

enum class VerificationResult { MATCH, MISMATCH, NOT_CHECKED, UNKNOWN }
enum class HumanAuthorizationState { REQUIRED, APPROVED, DENIED, UNKNOWN }
enum class LedgerIntegrity { VALID, CORRUPT_OR_TAMPERED }

/** Stable SWI identity. This record is repeated in every attempt and is never removed on file loss. */
data class RecoveryItemIdentity(
    val swiKey: String,
    val itemId: String,
    val lastKnownLocation: String,
    val expectedSha256: String?
)

/** One append-only observation. Approval is metadata, not an authorization decision by this app. */
data class RecoveryAttempt(
    val attemptId: String,
    val item: RecoveryItemIdentity,
    val detectedAt: String,
    val observedState: RecoveryState,
    val searchLocations: List<String>,
    val recoveryActions: List<String>,
    val observedContentSource: String?,
    val observedContentSha256: String?,
    val verification: VerificationResult,
    val uncertainty: String,
    val humanAuthorization: HumanAuthorizationState,
    val approvalRecordId: String?
)

data class LedgerSnapshot(
    val attempts: List<RecoveryAttempt>,
    val integrity: LedgerIntegrity
)

/**
 * Local append-only hash-chained journal. The chain detects accidental edits/torn writes; it is
 * not signed, remotely anchored, or proof of origin. A broken journal is preserved and refuses
 * further appends so recovery evidence is not silently rewritten.
 */
class RecoveryLedger(private val file: File) {
    @Synchronized
    fun append(attempt: RecoveryAttempt) {
        val snapshot = read()
        require(snapshot.integrity == LedgerIntegrity.VALID) {
            "Recovery ledger integrity is not valid; preserve and investigate it first"
        }
        val previous = if (file.exists() && file.length() > 0L) {
            file.readLines(Charsets.UTF_8).last().split('|').getOrNull(3)
                ?: error("Malformed final ledger record")
        } else {
            GENESIS
        }
        val payload = encodeFields(fields(attempt))
        val digest = chainHash(previous, payload)
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { stream ->
            if (file.length() > 0L) {
                val lastByte = file.inputStream().use { input ->
                    var value = -1
                    while (true) {
                        val next = input.read()
                        if (next < 0) break
                        value = next
                    }
                    value
                }
                require(lastByte == '\n'.code) { "Incomplete final ledger record; append refused" }
            }
            val line = "v1|$previous|$payload|$digest\n".toByteArray(StandardCharsets.UTF_8)
            stream.write(line)
            stream.fd.sync()
        }
    }

    @Synchronized
    fun read(): LedgerSnapshot {
        if (!file.exists() || file.length() == 0L) {
            return LedgerSnapshot(emptyList(), LedgerIntegrity.VALID)
        }
        val attempts = mutableListOf<RecoveryAttempt>()
        var previous = GENESIS
        try {
            val bytes = file.readBytes()
            if (bytes.last() != '\n'.code.toByte()) {
                return LedgerSnapshot(attempts, LedgerIntegrity.CORRUPT_OR_TAMPERED)
            }
            bytes.toString(StandardCharsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split('|')
                if (parts.size != 4 || parts[0] != "v1" || parts[1] != previous) {
                    return LedgerSnapshot(attempts, LedgerIntegrity.CORRUPT_OR_TAMPERED)
                }
                if (chainHash(parts[1], parts[2]) != parts[3]) {
                    return LedgerSnapshot(attempts, LedgerIntegrity.CORRUPT_OR_TAMPERED)
                }
                val values = decodeFields(parts[2])
                attempts += fromFields(values)
                previous = parts[3]
            }
            return LedgerSnapshot(attempts, LedgerIntegrity.VALID)
        } catch (_: Exception) {
            return LedgerSnapshot(attempts, LedgerIntegrity.CORRUPT_OR_TAMPERED)
        }
    }

    fun latestFor(swiKey: String, itemId: String): RecoveryAttempt? =
        read().attempts.lastOrNull { it.item.swiKey == swiKey && it.item.itemId == itemId }

    private fun fields(a: RecoveryAttempt): List<String> = listOf(
        a.attemptId, a.item.swiKey, a.item.itemId, a.item.lastKnownLocation,
        a.item.expectedSha256.orEmpty(), a.detectedAt, a.observedState.name,
        a.searchLocations.joinToString("\u0000"), a.recoveryActions.joinToString("\u0000"),
        a.observedContentSource.orEmpty(), a.observedContentSha256.orEmpty(),
        a.verification.name, a.uncertainty, a.humanAuthorization.name,
        a.approvalRecordId.orEmpty()
    )

    private fun fromFields(v: List<String>): RecoveryAttempt {
        require(v.size == 15)
        fun list(value: String) = if (value.isEmpty()) emptyList() else value.split('\u0000')
        return RecoveryAttempt(
            attemptId = v[0],
            item = RecoveryItemIdentity(v[1], v[2], v[3], v[4].ifEmpty { null }),
            detectedAt = v[5],
            observedState = RecoveryState.valueOf(v[6]),
            searchLocations = list(v[7]),
            recoveryActions = list(v[8]),
            observedContentSource = v[9].ifEmpty { null },
            observedContentSha256 = v[10].ifEmpty { null },
            verification = VerificationResult.valueOf(v[11]),
            uncertainty = v[12],
            humanAuthorization = HumanAuthorizationState.valueOf(v[13]),
            approvalRecordId = v[14].ifEmpty { null }
        )
    }

    private fun encodeFields(values: List<String>): String = values.joinToString(".") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeFields(value: String): List<String> = value.split('.').map {
        String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8)
    }

    private fun chainHash(previous: String, payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$previous\n$payload".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object { const val GENESIS = "SWI-RECOVERY-LEDGER-V1" }
}
