package com.naos.pikminbee.domain

import org.json.JSONObject
import org.json.JSONTokener

/**
 * Local decision model for #170. Revisions are supplied by the approved documents,
 * not invented from app versions or the permission-wizard completion flag.
 * This model does not grant Android permissions or enable optional data sharing.
 */
data class LegalDocumentRevisions(val terms: String, val privacy: String) {
    init {
        require(terms.isNotBlank() && privacy.isNotBlank())
        require(terms == terms.trim() && privacy == privacy.trim())
    }
}

data class LegalConsentReceipt(
    val termsRevision: String,
    val privacyRevision: String,
    val acceptedAtMillis: Long,
)

object LegalConsentPolicy {
    fun requiresReview(
        required: LegalDocumentRevisions,
        receipt: LegalConsentReceipt?,
    ): Boolean = receipt == null ||
        receipt.acceptedAtMillis <= 0L ||
        receipt.termsRevision != required.terms ||
        receipt.privacyRevision != required.privacy

    /**
     * Only an explicit confirmation of both displayed revisions can produce a
     * receipt. The caller must atomically persist it before unlocking the flow.
     * Refusal/withdrawal must clear any active persisted receipt separately.
     */
    fun confirmedReceipt(
        required: LegalDocumentRevisions,
        displayed: LegalDocumentRevisions,
        explicitlyAccepted: Boolean,
        acceptedAtMillis: Long,
    ): LegalConsentReceipt? {
        if (!explicitlyAccepted || displayed != required || acceptedAtMillis <= 0L) return null
        return LegalConsentReceipt(required.terms, required.privacy, acceptedAtMillis)
    }
}

/** Single-record representation for local storage; not itself a persistence or permission grant. */
object LegalConsentReceiptCodec {
    private const val SCHEMA = 1

    fun encode(receipt: LegalConsentReceipt): String {
        LegalDocumentRevisions(receipt.termsRevision, receipt.privacyRevision)
        require(receipt.acceptedAtMillis > 0L)
        return JSONObject()
            .put("schema", SCHEMA)
            .put("terms", receipt.termsRevision)
            .put("privacy", receipt.privacyRevision)
            .put("acceptedAtMillis", receipt.acceptedAtMillis)
            .toString()
    }

    /** Unknown schemas, missing fields, wrong types and invalid revisions require review. */
    fun decode(raw: String?): LegalConsentReceipt? {
        if (raw.isNullOrBlank()) return null
        return try {
            val tokener = JSONTokener(raw)
            val json = tokener.nextValue() as? JSONObject ?: return null
            if (tokener.nextClean() != '\u0000' || json.length() != 4 || json.opt("schema") != SCHEMA) {
                return null
            }
            val terms = json.opt("terms") as? String ?: return null
            val privacy = json.opt("privacy") as? String ?: return null
            val timestamp = when (val value = json.opt("acceptedAtMillis")) {
                is Int -> value.toLong()
                is Long -> value
                else -> return null
            }
            if (timestamp <= 0L) return null
            LegalDocumentRevisions(terms, privacy)
            LegalConsentReceipt(terms, privacy, timestamp)
        } catch (_: org.json.JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
