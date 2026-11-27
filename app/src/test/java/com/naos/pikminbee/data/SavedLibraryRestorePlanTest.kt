package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import org.junit.Assert.*
import org.junit.Test

class SavedLibraryRestorePlanTest {
    private val place = SavedPlace(1L, "Existing", Coordinate(0.0, 0.0))
    private val route = SavedRoute("r1", "Existing", WalkMode.ROUTE, listOf(place.coordinate), 1L)

    private fun snapshot(
        places: List<SavedPlace> = emptyList(),
        routes: List<SavedRoute> = emptyList(),
    ) = SavedDataBackupCodec.Snapshot(places, routes)

    @Test fun mergePreservesExistingOrderAndAppendsOnlyNewRecords() {
        val original = snapshot(listOf(place), listOf(route))
        val newPlace = place.copy(id = 2L)
        val newRoute = route.copy(id = "r2")
        val plan = SavedLibraryRestorePlan.prepare(
            original, snapshot(listOf(newPlace, place), listOf(newRoute, route)),
        )
        assertTrue(plan.hasChanges)
        assertEquals(snapshot(listOf(place, newPlace), listOf(route, newRoute)),
            plan.candidateIfCurrent(original))
    }

    @Test fun identicalBackupIsNoOp() {
        val original = snapshot(listOf(place), listOf(route))
        val plan = SavedLibraryRestorePlan.prepare(original, original)
        assertFalse(plan.hasChanges)
        assertEquals(original, plan.candidateIfCurrent(original))
    }

    @Test fun changedLibraryInvalidatesReviewedPlanEvenWithSameCounts() {
        val original = snapshot(listOf(place))
        val plan = SavedLibraryRestorePlan.prepare(original, snapshot(listOf(place.copy(id = 2L))))
        assertThrows(IllegalStateException::class.java) {
            plan.candidateIfCurrent(snapshot(listOf(place.copy(name = "Renamed"))))
        }
    }

    @Test fun conflictsAndRetiredIncomingRoutesAreRejected() {
        val original = snapshot(listOf(place))
        assertThrows(IllegalArgumentException::class.java) {
            SavedLibraryRestorePlan.prepare(original, snapshot(listOf(place.copy(name = "Conflict"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedLibraryRestorePlan.prepare(original, snapshot(routes = listOf(route.copy(mode = WalkMode.FLOWERS))))
        }
    }

    @Test fun callerMutationDoesNotAlterReviewedBackup() {
        val original = snapshot(listOf(place))
        val incoming = mutableListOf(place.copy(id = 2L))
        val plan = SavedLibraryRestorePlan.prepare(original, snapshot(incoming))
        incoming.clear()
        assertEquals(listOf(place, place.copy(id = 2L)), plan.candidateIfCurrent(original).places)
    }

    @Test fun combinedPlaceLimitBlocksRestoreButIdenticalRecordsDoNotConsumeCapacity() {
        val existing = (1L..10_000L).map { place.copy(id = it) }
        val original = snapshot(existing)
        assertFalse(SavedLibraryRestorePlan.prepare(original, snapshot(listOf(place))).hasChanges)
        val extra = place.copy(id = 10_001L)
        val preview = SavedDataRestorePreview.inspect(existing, emptyList(), listOf(extra), emptyList())
        assertTrue(SavedDataRestorePreview.Blocker.PLACE_CAPACITY in preview.blockers)
        assertThrows(IllegalArgumentException::class.java) {
            SavedLibraryRestorePlan.prepare(original, snapshot(listOf(extra)))
        }
    }

    @Test fun individuallyValidBackupsCanExceedCombinedByteLimit() {
        val metadata = "x".repeat(4096)
        val existing = (1L..300L).map { place.copy(id = it, city = metadata, countryName = metadata, timeZoneId = metadata, countryCode = metadata) }
        val incoming = existing.map { it.copy(id = it.id + 300L) }
        val original = snapshot(existing)
        val backup = snapshot(incoming)
        SavedDataBackupCodec.encode(original)
        SavedDataBackupCodec.encode(backup)
        assertThrows(IllegalArgumentException::class.java) {
            SavedLibraryRestorePlan.prepare(original, backup)
        }
    }
}
