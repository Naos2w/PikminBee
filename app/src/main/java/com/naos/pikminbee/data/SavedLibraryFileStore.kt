package com.naos.pikminbee.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Candidate single-file persistence for a complete saved library. Not wired to
 * AppPreferences: migration and ownership of all library writers must be reviewed.
 * One instance must own all reads/writes, with the file in app-private storage.
 * File sync and atomic replacement are required; directory durability across
 * power loss and Android filesystem behaviour still need device verification.
 */
internal class SavedLibraryFileStore(
    private val file: File,
    private val replace: (File, File) -> Unit = { source, target ->
        Files.move(source.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) {
    @Synchronized
    fun read(): SavedDataBackupCodec.Snapshot {
        // Missing or damaged storage is never interpreted as an empty library.
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() <= SavedDataBackupCodec.MAX_BYTES - count) {
                    "Library file exceeds capacity"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return SavedDataBackupCodec.decode(bytes)
    }

    /** Explicit initialization for a validated, independently reviewed migration. */
    @Synchronized
    fun initialize(snapshot: SavedDataBackupCodec.Snapshot) {
        check(Files.notExists(file.toPath())) { "Library already exists or cannot be checked" }
        write(snapshot)
    }

    /** Never overwrite a newer library or publish a partially written candidate. */
    @Synchronized
    fun restore(plan: SavedLibraryRestorePlan): Boolean {
        val candidate = plan.candidateIfCurrent(read())
        if (!plan.hasChanges) return false
        write(candidate)
        return true
    }

    private fun write(snapshot: SavedDataBackupCodec.Snapshot) {
        val bytes = SavedDataBackupCodec.encode(snapshot)
        val target = file.absoluteFile
        val parent = requireNotNull(target.parentFile)
        // Same directory ensures the rename cannot cross filesystem boundaries.
        val temporary = File.createTempFile(".library-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            // Fail closed if atomic replacement is unsupported; no copy fallback.
            replace(temporary, target)
        } finally {
            // Do not mask the original write error or report a successful rename
            // as failed solely because an unused temporary name cannot be removed.
            try {
                Files.deleteIfExists(temporary.toPath())
            } catch (_: IOException) {
                // An orphan must never be treated as a committed library.
            } catch (_: SecurityException) {
                // A denied cleanup also must not hide the original result.
            }
        }
    }
}
