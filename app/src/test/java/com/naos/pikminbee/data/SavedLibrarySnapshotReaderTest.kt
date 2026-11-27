package com.naos.pikminbee.data

import com.naos.pikminbee.domain.WalkMode
import org.junit.Assert.*
import org.junit.Test

class SavedLibrarySnapshotReaderTest {
    private val place = """{"id":123,"name":"地點","lat":25.04,"lon":121.5}"""
    private val route = """{"id":"route-1","name":"路線","mode":"ROUTE","points":[{"lat":25,"lon":121}]}"""

    @Test fun missingLibrariesAreEmpty() {
        val result = SavedLibrarySnapshotReader.read(null, null)
        assertTrue(result.places.isEmpty())
        assertTrue(result.routes.isEmpty())
    }

    @Test fun readsCompleteLibraryAndLegacyDefaults() {
        val result = SavedLibrarySnapshotReader.read("[$place]", "[$route]")
        assertEquals(123L, result.places.single().createdAtMillis)
        assertEquals(0L, result.routes.single().createdAtMillis)
        assertEquals(result, SavedDataBackupCodec.decode(SavedDataBackupCodec.encode(result)))
    }

    @Test fun refusesMalformedRecordInsteadOfPartialBackup() {
        rejects("[$place, null]", "[$route]")
        rejects("[$place]", "[$route, {}]")
        rejects("[$place", "[$route]")
        rejects("[$place] extra", "[$route]")
    }

    @Test fun refusesCoercedTypesAndInvalidCoordinates() {
        rejects("[${place.replace("123", "\"123\"")}]", null)
        rejects("[${place.replace("25.04", "\"25.04\"")}]", null)
        rejects("[${place.replace("25.04", "91")}]", null)
    }

    @Test fun refusesDuplicateIdsAcrossEachLibrary() {
        rejects("[$place,$place]", null)
        rejects(null, "[$route,$route]")
    }

    @Test fun refusesUnknownFieldsToAvoidLosingData() {
        rejects("[${place.dropLast(1)},\"futureField\":true}]", null)
        rejects(null, "[${route.replace("\"lat\":25", "\"future\":1,\"lat\":25")}]")
    }

    @Test fun retainsHistoricalRouteAsDataOnly() {
        val result = SavedLibrarySnapshotReader.read(null, "[${route.replace("ROUTE", "FLOWERS")}]")
        assertEquals(WalkMode.FLOWERS, result.routes.single().mode)
        assertFalse(SavedDataRestorePreview.inspect(emptyList(), emptyList(),
            result.places, result.routes).canProceed)
    }

    private fun rejects(places: String?, routes: String?) {
        assertTrue("Invalid library must not return a partial snapshot",
            runCatching { SavedLibrarySnapshotReader.read(places, routes) }.isFailure)
    }
}
