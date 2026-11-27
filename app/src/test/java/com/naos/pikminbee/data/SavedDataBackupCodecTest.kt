package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SavedDataBackupCodecTest {
    private val point = Coordinate(25.123456789, -179.9999999)
    private val place = SavedPlace(1L, "收藏 🗺", point, "TW", "臺灣", "台北", "Asia/Taipei", 2L)
    private val route = SavedRoute("route-1", "步道", WalkMode.ROUTE, listOf(point, Coordinate(0.0, 0.0)), 3L)
    private val snapshot = SavedDataBackupCodec.Snapshot(listOf(place), listOf(route))
    private val empty = SavedDataBackupCodec.Snapshot(emptyList(), emptyList())

    @Test fun roundTripPreservesEveryFieldAndOrder() {
        val original = SavedDataBackupCodec.Snapshot(
            listOf(place, place.copy(id = 2, countryCode = null, countryName = "", city = null, timeZoneId = null)),
            listOf(route, route.copy(id = "history", mode = WalkMode.FLOWERS)),
        )
        val bytes = SavedDataBackupCodec.encode(original)
        val decoded = SavedDataBackupCodec.decode(bytes)
        assertEquals(original, decoded)
        assertArrayEquals(bytes, SavedDataBackupCodec.encode(decoded))
        val preview = SavedDataRestorePreview.inspect(emptyList(), emptyList(), decoded.places, decoded.routes)
        assertFalse(preview.canProceed)
        assertTrue(SavedDataRestorePreview.Blocker.LEGACY_ROUTE_REVIEW in preview.blockers)
    }

    @Test fun emptySnapshotIsSupported() {
        assertEquals(empty, SavedDataBackupCodec.decode(SavedDataBackupCodec.encode(empty)))
    }

    @Test fun damageAndTruncationAreRejected() {
        val bytes = SavedDataBackupCodec.encode(snapshot)
        val damaged = bytes.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        rejected { SavedDataBackupCodec.decode(damaged) }
        rejected { SavedDataBackupCodec.decode(bytes.copyOf(bytes.size - 1)) }
        rejected { SavedDataBackupCodec.decode(ByteArray(SavedDataBackupCodec.MAX_BYTES + 1)) }
    }

    @Test fun unknownVersionAndMagicRejectedEvenWithValidDigest() {
        val payload = payload(empty)
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putInt(4, 2) })) }
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putInt(0, 0) })) }
    }

    @Test fun malformedCountAndTrailingBytesRejectedEvenWithValidDigest() {
        val payload = payload(empty)
        for (count in listOf(-1, 10001, Int.MAX_VALUE)) {
            rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putInt(8, count) })) }
        }
        rejected { SavedDataBackupCodec.decode(sign(payload + byteArrayOf(0))) }
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf(payload.size - 1))) }
    }

    @Test fun invalidUtf8AndOversizedLengthAreNotSilentlyNormalised() {
        val payload = payload(snapshot)
        // Header/count (12), ID (8), name length (4), then name bytes.
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { it[24] = 0xff.toByte() })) }
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putInt(20, 4097) })) }
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putInt(20, -1) })) }
    }

    @Test fun invalidCoordinatesAndIdAreRejected() {
        val payload = payload(snapshot)
        val latitudeOffset = 24 + place.name.toByteArray(Charsets.UTF_8).size
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putDouble(latitudeOffset, Double.NaN) })) }
        rejected { SavedDataBackupCodec.decode(sign(payload.copyOf().also { ByteBuffer.wrap(it).putLong(12, 0L) })) }
    }

    @Test fun duplicateRecordsAndOversizedTextAreRejectedOnEncode() {
        rejected { SavedDataBackupCodec.encode(snapshot.copy(places = listOf(place, place))) }
        rejected { SavedDataBackupCodec.encode(snapshot.copy(routes = listOf(route, route))) }
        rejected { SavedDataBackupCodec.encode(snapshot.copy(places = listOf(place.copy(city = "界".repeat(2000))))) }
    }

    @Test fun unknownRouteModeAndInvalidStopCountAreRejected() {
        val bytes = payload(snapshot.copy(places = emptyList()))
        val modeOffset = 16 + 4 + route.id.toByteArray().size + 4 + route.name.toByteArray().size
        rejected { SavedDataBackupCodec.decode(sign(bytes.copyOf().also { it[modeOffset] = 9 })) }
        rejected { SavedDataBackupCodec.decode(sign(bytes.copyOf().also { ByteBuffer.wrap(it).putInt(modeOffset + 1, 16) })) }
    }

    @Test fun streamReadSupportsShortReadsWithoutAvailableAndDoesNotClose() {
        val bytes = SavedDataBackupCodec.encode(snapshot)
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun available() = 0
            override fun read(b: ByteArray, off: Int, len: Int) =
                super.read(b, off, minOf(len, 3))
            override fun close() { closed = true }
        }
        assertEquals(snapshot, SavedDataBackupCodec.readFrom(input))
        assertFalse(closed)
    }

    @Test fun streamReadStopsAtCapacityPlusOne() {
        var consumed = 0
        val input = object : InputStream() {
            override fun read(): Int { consumed++; return 0 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                consumed += len
                return len
            }
        }
        rejected { SavedDataBackupCodec.readFrom(input) }
        assertEquals(SavedDataBackupCodec.MAX_BYTES + 1, consumed)
    }

    @Test fun stalledAndFailedStreamsDoNotReturnPartialSnapshots() {
        val stalled = object : InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int) = 0
        }
        assertThrows(IOException::class.java) { SavedDataBackupCodec.readFrom(stalled) }
        val failed = object : InputStream() {
            override fun read(): Int = throw IOException("Injected")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("Injected")
        }
        assertThrows(IOException::class.java) { SavedDataBackupCodec.readFrom(failed) }
        val bytes = SavedDataBackupCodec.encode(snapshot)
        rejected { SavedDataBackupCodec.readFrom(ByteArrayInputStream(bytes.copyOf(bytes.size - 1))) }
    }

    @Test fun privateWriteValidatesBeforeWritingAndLeavesStreamOpen() {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true }
        }
        rejected { SavedDataBackupCodec.writeToPrivateStorage(snapshot.copy(places = listOf(place, place)), output) }
        assertEquals(0, output.size())
        SavedDataBackupCodec.writeToPrivateStorage(snapshot, output)
        assertArrayEquals(SavedDataBackupCodec.encode(snapshot), output.toByteArray())
        assertFalse(closed)
    }

    @Test fun privateWritePropagatesIoFailure() {
        val output = object : OutputStream() {
            override fun write(b: Int) { throw IOException("Injected") }
        }
        assertThrows(IOException::class.java) {
            SavedDataBackupCodec.writeToPrivateStorage(snapshot, output)
        }
    }

    private fun payload(snapshot: SavedDataBackupCodec.Snapshot): ByteArray =
        SavedDataBackupCodec.encode(snapshot).let { it.copyOf(it.size - 32) }

    private fun sign(payload: ByteArray): ByteArray =
        payload + MessageDigest.getInstance("SHA-256").digest(payload)

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid backup to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
