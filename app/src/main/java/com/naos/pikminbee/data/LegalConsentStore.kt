package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.domain.LegalConsentPolicy
import com.naos.pikminbee.domain.LegalConsentReceiptCodec
import com.naos.pikminbee.domain.LegalDocumentRevisions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Storage failures are observable; callers must not unlock on a failed write. */
internal interface LegalConsentStorage {
    @Throws(IOException::class)
    fun read(): String?

    @Throws(IOException::class)
    fun replace(value: String)
}

/** One private record, replaced atomically; never fall back to a partial overwrite. */
internal class FileLegalConsentStorage(private val file: File) : LegalConsentStorage {
    override fun read(): String? {
        if (!file.exists()) return null
        return file.inputStream().use { input ->
            val bytes = ByteArray(MAX_BYTES + 1)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            if (count > MAX_BYTES) null else bytes.copyOf(count).toString(Charsets.UTF_8)
        }
    }

    override fun replace(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val parent = file.absoluteFile.parentFile ?: throw IOException("Missing consent directory")
        if (!parent.isDirectory) throw IOException("Missing consent directory")
        val temporary = Files.createTempFile(parent.toPath(), "legal-consent-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use {
                it.write(bytes)
                it.fd.sync()
            }
            Files.move(
                temporary, file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        const val MAX_BYTES = 16 * 1024
    }
}

/**
 * Process-local owner of legal consent. Keep one instance for all future entry points.
 * Only a successful explicit confirmation clears the in-process failure latch.
 * Withdrawal replaces the receipt with a non-receipt rather than deleting other data.
 * This foundation does not start services, grant permissions or enforce UI access yet.
 */
class LegalConsentStore internal constructor(private val storage: LegalConsentStorage) {
    private var blocked = false

    @Synchronized
    fun requiresReview(required: LegalDocumentRevisions): Boolean {
        if (blocked) return true
        return try {
            LegalConsentPolicy.requiresReview(required, LegalConsentReceiptCodec.decode(storage.read()))
        } catch (_: IOException) {
            blocked = true
            true
        } catch (_: SecurityException) {
            blocked = true
            true
        }
    }

    @Synchronized
    fun confirm(
        required: LegalDocumentRevisions,
        displayed: LegalDocumentRevisions,
        explicitlyAccepted: Boolean,
        acceptedAtMillis: Long,
    ): Boolean {
        val receipt = LegalConsentPolicy.confirmedReceipt(
            required, displayed, explicitlyAccepted, acceptedAtMillis,
        ) ?: return false
        blocked = true
        return try {
            val encoded = LegalConsentReceiptCodec.encode(receipt)
            storage.replace(encoded)
            if (LegalConsentReceiptCodec.decode(storage.read()) != receipt) return false
            blocked = false
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** False means durable withdrawal was not verified; stop access and require retry. */
    @Synchronized
    fun withdraw(): Boolean {
        blocked = true
        return try {
            storage.replace(WITHDRAWN)
            storage.read() == WITHDRAWN
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        private const val WITHDRAWN = "withdrawn"
        private var instance: LegalConsentStore? = null

        /** noBackupFilesDir avoids copying consent to a different installation. */
        @Synchronized
        fun get(context: Context): LegalConsentStore = instance ?: LegalConsentStore(
            FileLegalConsentStorage(File(context.applicationContext.noBackupFilesDir, "legal-consent.json")),
        ).also { instance = it }
    }
}
