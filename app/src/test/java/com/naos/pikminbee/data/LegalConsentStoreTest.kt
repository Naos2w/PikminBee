package com.naos.pikminbee.data

import com.naos.pikminbee.domain.LegalDocumentRevisions
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegalConsentStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val current = LegalDocumentRevisions("terms-r1", "privacy-r1")

    private class MemoryStorage : LegalConsentStorage {
        var value: String? = null
        var failWrite = false
        var failRead = false
        var ignoreWrite = false
        override fun read(): String? {
            if (failRead) throw IOException("read failure")
            return value
        }
        override fun replace(value: String) {
            if (failWrite) throw IOException("write failure")
            if (!ignoreWrite) this.value = value
        }
    }

    @Test fun noRecordAndRefusalNeverUnlock() {
        val storage = MemoryStorage()
        val store = LegalConsentStore(storage)
        assertTrue(store.requiresReview(current))
        assertFalse(store.confirm(current, current, false, 123L))
        assertFalse(store.confirm(current, current.copy(terms = "old"), true, 123L))
        assertEquals(null, storage.value)
        assertTrue(store.requiresReview(current))
    }

    @Test fun actualFileSurvivesRestartAndRevisionChangeRequiresReview() {
        val file = File(folder.root, "legal-consent.json")
        val store = LegalConsentStore(FileLegalConsentStorage(file))
        assertTrue(store.confirm(current, current, true, 123L))
        val restarted = LegalConsentStore(FileLegalConsentStorage(file))
        assertFalse(restarted.requiresReview(current))
        assertTrue(restarted.requiresReview(current.copy(terms = "terms-r2")))
        assertTrue(restarted.requiresReview(current.copy(privacy = "privacy-r2")))
        assertEquals(listOf("legal-consent.json"), folder.root.list()!!.toList())
    }

    @Test fun actualWithdrawalSurvivesRestartAndPreservesUnrelatedFile() {
        val file = File(folder.root, "legal-consent.json")
        val unrelated = File(folder.root, "saved-places.json").apply { writeText("keep") }
        val store = LegalConsentStore(FileLegalConsentStorage(file))
        assertTrue(store.confirm(current, current, true, 123L))
        assertTrue(store.withdraw())
        assertTrue(store.requiresReview(current))
        val restarted = LegalConsentStore(FileLegalConsentStorage(file))
        assertTrue(restarted.requiresReview(current))
        assertEquals("keep", unrelated.readText())
        assertTrue(restarted.confirm(current, current, true, 456L))
        assertFalse(restarted.requiresReview(current))
    }

    @Test fun corruptedAndOversizedFileRequireReview() {
        val file = File(folder.root, "legal-consent.json")
        val store = LegalConsentStore(FileLegalConsentStorage(file))
        file.writeText("{invalid")
        assertTrue(store.requiresReview(current))
        file.writeText("x".repeat(16 * 1024 + 1))
        assertTrue(store.requiresReview(current))
    }

    @Test fun failedConfirmationDoesNotReusePreviouslyValidReceipt() {
        val storage = MemoryStorage()
        val store = LegalConsentStore(storage)
        assertTrue(store.confirm(current, current, true, 123L))
        storage.failWrite = true
        assertFalse(store.confirm(current, current, true, 456L))
        assertTrue(store.requiresReview(current))
        storage.failWrite = false
        assertTrue(store.requiresReview(current))
        assertTrue(store.confirm(current, current, true, 456L))
        assertFalse(store.requiresReview(current))
    }

    @Test fun failedWithdrawalIsReportedAndBlocksUntilSuccessfulConfirmation() {
        val storage = MemoryStorage()
        val store = LegalConsentStore(storage)
        assertTrue(store.confirm(current, current, true, 123L))
        storage.failWrite = true
        assertFalse(store.withdraw())
        assertTrue(store.requiresReview(current))
        storage.failWrite = false
        assertTrue(store.withdraw())
        assertTrue(LegalConsentStore(storage).requiresReview(current))
        assertTrue(store.requiresReview(current))
    }

    @Test fun ignoredWriteAndReadFailureCannotUnlock() {
        val storage = MemoryStorage()
        val store = LegalConsentStore(storage)
        storage.ignoreWrite = true
        assertFalse(store.confirm(current, current, true, 123L))
        assertTrue(store.requiresReview(current))
        storage.ignoreWrite = false
        storage.failRead = true
        assertFalse(store.confirm(current, current, true, 123L))
        storage.failRead = false
        assertTrue(store.requiresReview(current))
    }

    @Test fun missingDirectoryFailsWithoutCreatingReceipt() {
        val file = File(File(folder.root, "missing"), "legal-consent.json")
        val store = LegalConsentStore(FileLegalConsentStorage(file))
        assertFalse(store.confirm(current, current, true, 123L))
        assertTrue(store.requiresReview(current))
        assertFalse(file.exists())
    }
}
