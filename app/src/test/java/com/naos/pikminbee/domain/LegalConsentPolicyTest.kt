package com.naos.pikminbee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalConsentPolicyTest {
    private val current = LegalDocumentRevisions("terms-r1", "privacy-r1")
    private val valid = LegalConsentReceipt("terms-r1", "privacy-r1", 123L)

    @Test fun missingReceiptRequiresReview() {
        assertTrue(LegalConsentPolicy.requiresReview(current, null))
    }

    @Test fun matchingReceiptSurvivesRestart() {
        assertFalse(LegalConsentPolicy.requiresReview(current, valid.copy()))
    }

    @Test fun changingEitherDocumentRequiresReview() {
        assertTrue(LegalConsentPolicy.requiresReview(current.copy(terms = "terms-r2"), valid))
        assertTrue(LegalConsentPolicy.requiresReview(current.copy(privacy = "privacy-r2"), valid))
    }

    @Test fun invalidTimestampAndRevisionFailClosed() {
        assertTrue(LegalConsentPolicy.requiresReview(current, valid.copy(acceptedAtMillis = 0L)))
        assertTrue(LegalConsentPolicy.requiresReview(current, valid.copy(acceptedAtMillis = -1L)))
        assertTrue(LegalConsentPolicy.requiresReview(current, valid.copy(termsRevision = "")))
    }

    @Test fun refusalAndStaleDisplayedDocumentsCannotCreateReceipt() {
        assertNull(LegalConsentPolicy.confirmedReceipt(current, current, false, 123L))
        assertNull(LegalConsentPolicy.confirmedReceipt(current, current.copy(privacy = "old"), true, 123L))
        assertNull(LegalConsentPolicy.confirmedReceipt(current, current, true, 0L))
    }

    @Test fun explicitMatchingConfirmationCreatesValidReceipt() {
        val receipt = LegalConsentPolicy.confirmedReceipt(current, current, true, 123L)
        assertFalse(LegalConsentPolicy.requiresReview(current, receipt))
    }

    @Test fun receiptRoundTripRetainsBothRevisionsAndLongTimestamp() {
        val receipt = LegalConsentReceipt("terms-2026", "privacy-2026", 1795694400000L)
        val decoded = LegalConsentReceiptCodec.decode(LegalConsentReceiptCodec.encode(receipt))
        assertEquals(receipt, decoded)
        assertFalse(LegalConsentPolicy.requiresReview(LegalDocumentRevisions("terms-2026", "privacy-2026"), decoded))
    }

    @Test fun missingOrMalformedStorageRequiresReview() {
        listOf(null, "", "  ", "{", "[]", "null", "{}", LegalConsentReceiptCodec.encode(valid) + "{}").forEach {
            val decoded = LegalConsentReceiptCodec.decode(it)
            assertNull(decoded)
            assertTrue(LegalConsentPolicy.requiresReview(current, decoded))
        }
    }

    @Test fun unknownSchemaAndWrongFieldTypesAreRejected() {
        listOf(
            """{"schema":2,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":123}""",
            """{"schema":"1","terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":123}""",
            """{"schema":1,"terms":1,"privacy":"privacy-r1","acceptedAtMillis":123}""",
            """{"schema":1,"terms":"terms-r1","privacy":null,"acceptedAtMillis":123}""",
            """{"schema":1,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":"123"}""",
            """{"schema":1,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":123.5}""",
            """{"schema":1,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":0}""",
            """{"schema":1,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":-1}""",
            """{"schema":1,"terms":"terms-r1","privacy":" ","acceptedAtMillis":123}""",
            """{"schema":1,"terms":" terms-r1","privacy":"privacy-r1","acceptedAtMillis":123}""",
            """{"schema":1,"terms":"terms-r1","privacy":"privacy-r1","acceptedAtMillis":123,"extra":true}""",
        ).forEach { assertNull(it, LegalConsentReceiptCodec.decode(it)) }
    }

    @Test fun storedOldDocumentRevisionsStillRequireReview() {
        val decoded = LegalConsentReceiptCodec.decode(LegalConsentReceiptCodec.encode(valid))
        assertEquals(valid, decoded)
        assertTrue(LegalConsentPolicy.requiresReview(current.copy(terms = "terms-r2"), decoded))
        assertTrue(LegalConsentPolicy.requiresReview(current.copy(privacy = "privacy-r2"), decoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidReceiptCannotBeEncoded() {
        LegalConsentReceiptCodec.encode(valid.copy(acceptedAtMillis = 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankDocumentRevisionRejected() {
        LegalDocumentRevisions(" ", "privacy-r1")
    }
}
