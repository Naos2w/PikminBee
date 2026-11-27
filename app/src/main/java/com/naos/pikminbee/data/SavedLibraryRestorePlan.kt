package com.naos.pikminbee.data

/**
 * Prepare a merge-only restore without touching preferences or the active
 * draft. A storage adapter must recheck the baseline while holding both
 * library locks, then durably persist both libraries together.
 */
internal class SavedLibraryRestorePlan private constructor(
    private val baseline: ByteArray,
    private val candidate: ByteArray,
) {
    /** Revalidate the exact reviewed library, not just record counts. */
    fun candidateIfCurrent(current: SavedDataBackupCodec.Snapshot): SavedDataBackupCodec.Snapshot {
        check(baseline.contentEquals(SavedDataBackupCodec.encode(current))) {
            "Library changed after restore preview"
        }
        return SavedDataBackupCodec.decode(candidate)
    }

    val hasChanges: Boolean get() = !baseline.contentEquals(candidate)

    companion object {
        fun prepare(
            existing: SavedDataBackupCodec.Snapshot,
            backup: SavedDataBackupCodec.Snapshot,
        ): SavedLibraryRestorePlan {
            // Canonical copies prevent a caller's mutable lists from changing
            // the reviewed plan or bypassing input validation.
            val baseline = SavedDataBackupCodec.encode(existing)
            val old = SavedDataBackupCodec.decode(baseline)
            val incoming = SavedDataBackupCodec.decode(SavedDataBackupCodec.encode(backup))
            val preview = SavedDataRestorePreview.inspect(
                old.places, old.routes, incoming.places, incoming.routes,
            )
            require(preview.canProceed) { "Restore blocked: ${preview.blockers}" }
            val placeIds = old.places.mapTo(HashSet()) { it.id }
            val routeIds = old.routes.mapTo(HashSet()) { it.id }
            val merged = SavedDataBackupCodec.Snapshot(
                old.places + incoming.places.filterNot { it.id in placeIds },
                old.routes + incoming.routes.filterNot { it.id in routeIds },
            )
            // Individual inputs can fit the format but their union may not.
            return SavedLibraryRestorePlan(baseline, SavedDataBackupCodec.encode(merged))
        }
    }
}
