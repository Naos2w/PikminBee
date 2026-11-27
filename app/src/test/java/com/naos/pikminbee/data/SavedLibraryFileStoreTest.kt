package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedLibraryFileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val place = SavedPlace(1L, "Existing", Coordinate(0.0, 0.0))
    private val route = SavedRoute("r1", "Existing", WalkMode.ROUTE, listOf(place.coordinate), 1L)
    private val baseline = SavedDataBackupCodec.Snapshot(listOf(place), listOf(route))
    private val incoming = SavedDataBackupCodec.Snapshot(
        listOf(place.copy(id = 2L)), listOf(route.copy(id = "r2")),
    )
    private fun target() = File(folder.root, "library.bin")

    @Test fun completeSnapshotSurvivesReopening() {
        val file = target()
        val store = SavedLibraryFileStore(file)
        store.initialize(baseline)
        val plan = SavedLibraryRestorePlan.prepare(baseline, incoming)
        assertTrue(store.restore(plan))
        assertEquals(plan.candidateIfCurrent(baseline), SavedLibraryFileStore(file).read())
        assertEquals(listOf("library.bin"), folder.root.list()!!.toList())
    }

    @Test fun failedAtomicReplacementLeavesOriginalBytesAndBothLibraries() {
        val file = target()
        SavedLibraryFileStore(file).initialize(baseline)
        val before = file.readBytes()
        val failing = SavedLibraryFileStore(file) { source, _ ->
            assertEquals(SavedLibraryRestorePlan.prepare(baseline, incoming).candidateIfCurrent(baseline),
                SavedDataBackupCodec.decode(source.readBytes()))
            throw IOException("Injected failure before replacement")
        }
        assertThrows(IOException::class.java) {
            failing.restore(SavedLibraryRestorePlan.prepare(baseline, incoming))
        }
        assertArrayEquals(before, file.readBytes())
        assertEquals(baseline, SavedLibraryFileStore(file).read())
        assertEquals(listOf("library.bin"), folder.root.list()!!.toList())
    }

    @Test fun unsupportedAtomicMoveDoesNotFallBackToCopy() {
        val file = target()
        SavedLibraryFileStore(file).initialize(baseline)
        val before = file.readBytes()
        val failing = SavedLibraryFileStore(file) { source, dest ->
            throw AtomicMoveNotSupportedException(source.path, dest.path, "Injected")
        }
        assertThrows(AtomicMoveNotSupportedException::class.java) {
            failing.restore(SavedLibraryRestorePlan.prepare(baseline, incoming))
        }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun stalePlanDoesNotReplaceNewerData() {
        val file = target()
        val store = SavedLibraryFileStore(file)
        store.initialize(baseline)
        val plan = SavedLibraryRestorePlan.prepare(baseline, incoming)
        store.restore(plan)
        val before = file.readBytes()
        assertThrows(IllegalStateException::class.java) { store.restore(plan) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun noOpDoesNotInvokeReplacement() {
        val file = target()
        SavedLibraryFileStore(file).initialize(baseline)
        val store = SavedLibraryFileStore(file) { _, _ -> fail("Unexpected write") }
        assertFalse(store.restore(SavedLibraryRestorePlan.prepare(baseline, baseline)))
    }

    @Test fun missingAndCorruptFilesAreNotTreatedAsEmpty() {
        val file = target()
        val store = SavedLibraryFileStore(file)
        val plan = SavedLibraryRestorePlan.prepare(baseline, incoming)
        assertThrows(IOException::class.java) { store.restore(plan) }
        assertFalse(file.exists())
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) { store.restore(plan) }
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertThrows(IllegalStateException::class.java) { store.initialize(baseline) }
    }

    @Test fun oversizedFileIsRejectedWithoutReplacement() {
        val file = target()
        file.writeBytes(ByteArray(SavedDataBackupCodec.MAX_BYTES + 1))
        val store = SavedLibraryFileStore(file) { _, _ -> fail("Unexpected write") }
        assertThrows(IllegalArgumentException::class.java) {
            store.restore(SavedLibraryRestorePlan.prepare(baseline, incoming))
        }
        assertEquals(SavedDataBackupCodec.MAX_BYTES.toLong() + 1, file.length())
    }
}
