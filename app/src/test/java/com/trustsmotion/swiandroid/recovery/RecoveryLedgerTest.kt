package com.trustsmotion.swiandroid.recovery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryLedgerTest {
    private fun attempt(state: RecoveryState = RecoveryState.SOURCE_UNAVAILABLE_UNKNOWN) = RecoveryAttempt(
        attemptId = "attempt-1",
        item = RecoveryItemIdentity("swi-key-7", "item-7", "content://provider/original", "abc123"),
        detectedAt = "2026-11-11T00:00:00Z",
        observedState = state,
        searchLocations = listOf("content://provider/authorized-search"),
        recoveryActions = listOf("USER_SELECTED", "HASH_CHECK"),
        observedContentSource = null,
        observedContentSha256 = null,
        verification = VerificationResult.UNKNOWN,
        uncertainty = "Location state unresolved",
        humanAuthorization = HumanAuthorizationState.REQUIRED,
        approvalRecordId = null
    )

    @Test fun preservesIdentityAndMetadataAcrossReadAndAppend() {
        val file = File.createTempFile("swi-recovery", ".ledger").apply { delete() }
        try {
            val ledger = RecoveryLedger(file)
            ledger.append(attempt())
            val second = attempt(RecoveryState.ACCESS_DENIED).copy(attemptId = "attempt-2")
            ledger.append(second)
            val snapshot = ledger.read()
            assertEquals(LedgerIntegrity.VALID, snapshot.integrity)
            assertEquals(2, snapshot.attempts.size)
            assertEquals("swi-key-7", snapshot.attempts.last().item.swiKey)
            assertEquals("item-7", snapshot.attempts.last().item.itemId)
            assertEquals("abc123", snapshot.attempts.last().item.expectedSha256)
            assertEquals(HumanAuthorizationState.REQUIRED, snapshot.attempts.last().humanAuthorization)
        } finally { file.delete() }
    }

    @Test fun changedJournalFailsClosedAndIsNotRewritten() {
        val file = File.createTempFile("swi-recovery", ".ledger")
        try {
            val ledger = RecoveryLedger(file)
            ledger.append(attempt())
            val original = file.readBytes()
            file.writeBytes(original.copyOf().also { it[3] = 'x'.code.toByte() })
            assertEquals(LedgerIntegrity.CORRUPT_OR_TAMPERED, ledger.read().integrity)
            val corrupted = file.readBytes()
            try { ledger.append(attempt().copy(attemptId = "attempt-2")); throw AssertionError("append should fail") }
            catch (_: IllegalArgumentException) { }
            assertTrue(corrupted.contentEquals(file.readBytes()))
        } finally { file.delete() }
    }

    @Test fun unknownBaselineNeverReportsCandidateAsVerified() {
        val candidate = attempt(RecoveryState.CANDIDATE_INTEGRITY_UNKNOWN).copy(
            item = RecoveryItemIdentity("k", "i", "uri", null),
            verification = VerificationResult.UNKNOWN,
            observedContentSource = "content://user-selected/candidate"
        )
        val file = File.createTempFile("swi-recovery", ".ledger")
        try {
            val ledger = RecoveryLedger(file)
            ledger.append(candidate)
            val restored = ledger.read().attempts.single()
            assertNull(restored.item.expectedSha256)
            assertEquals(VerificationResult.UNKNOWN, restored.verification)
            assertNotEquals("RECOVERED", restored.observedState.name)
        } finally { file.delete() }
    }
}
