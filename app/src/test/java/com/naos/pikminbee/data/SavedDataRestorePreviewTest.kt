package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import org.junit.Assert.*
import org.junit.Test

class SavedDataRestorePreviewTest {
    private val point = Coordinate(0.0, 0.0)
    private val place = SavedPlace(1L, "Example", point, city = "Example city")
    private val route = SavedRoute("route-1", "Example route", WalkMode.ROUTE, listOf(point), 1L)

    @Test fun identicalRecordsAreNotCountedAsNew() {
        val result = SavedDataRestorePreview.inspect(listOf(place), listOf(route), listOf(place), listOf(route))
        assertTrue(result.canProceed)
        assertEquals(SavedDataRestorePreview.Counts(0, 1, 0), result.places)
        assertEquals(SavedDataRestorePreview.Counts(0, 1, 0), result.routes)
    }

    @Test fun distinctIdsAreNewEvenWhenCoordinatesMatch() {
        val result = SavedDataRestorePreview.inspect(
            listOf(place), listOf(route), listOf(place.copy(id = 2L)), listOf(route.copy(id = "route-2")),
        )
        assertTrue(result.canProceed)
        assertEquals(1, result.places.new)
        assertEquals(1, result.routes.new)
    }

    @Test fun sameIdWithDifferentNameOrMetadataBlocksOverwrite() {
        val result = SavedDataRestorePreview.inspect(
            listOf(place), listOf(route), listOf(place.copy(city = "Changed")), listOf(route.copy(name = "Changed")),
        )
        assertFalse(result.canProceed)
        assertEquals(1, result.places.conflicting)
        assertEquals(1, result.routes.conflicting)
        assertTrue(SavedDataRestorePreview.Blocker.ID_CONFLICT in result.blockers)
        assertEquals("Example city", place.city)
        assertEquals("Example route", route.name)
    }

    @Test fun duplicateIdsInEitherLibraryBlockRestore() {
        val result = SavedDataRestorePreview.inspect(
            listOf(place, place), emptyList(), emptyList(), listOf(route, route),
        )
        assertTrue(SavedDataRestorePreview.Blocker.DUPLICATE_EXISTING_ID in result.blockers)
        assertTrue(SavedDataRestorePreview.Blocker.DUPLICATE_BACKUP_ID in result.blockers)
        assertFalse(result.canProceed)
    }

    @Test fun existingRouteLimitIncludesNewButNotIdenticalRecords() {
        val existing = (1..100).map { route.copy(id = "route-$it") }
        val duplicate = SavedDataRestorePreview.inspect(emptyList(), existing, emptyList(), listOf(route))
        assertTrue(duplicate.canProceed)
        val extra = SavedDataRestorePreview.inspect(emptyList(), existing, emptyList(), listOf(route.copy(id = "new")))
        assertTrue(SavedDataRestorePreview.Blocker.ROUTE_CAPACITY in extra.blockers)
        assertFalse(extra.canProceed)
    }

    @Test fun legacyModeRequiresReviewWithoutModifyingExistingRecords() {
        val legacy = route.copy(mode = WalkMode.FLOWERS)
        val result = SavedDataRestorePreview.inspect(emptyList(), listOf(legacy), emptyList(), listOf(legacy))
        assertFalse(result.canProceed)
        assertTrue(SavedDataRestorePreview.Blocker.LEGACY_ROUTE_REVIEW in result.blockers)
        assertEquals(WalkMode.FLOWERS, legacy.mode)
    }

    @Test fun invalidNamesIdsAndTimestampsAreRejected() {
        val result = SavedDataRestorePreview.inspect(
            emptyList(), emptyList(), listOf(place.copy(id = 0L, name = "")),
            listOf(route.copy(createdAtMillis = -1L)),
        )
        assertFalse(result.canProceed)
        assertTrue(SavedDataRestorePreview.Blocker.INVALID_PLACE in result.blockers)
        assertTrue(SavedDataRestorePreview.Blocker.INVALID_ROUTE in result.blockers)
    }
}
