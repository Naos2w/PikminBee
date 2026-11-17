package com.naos.pikminbee.location

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test

class CheckpointCodecTest {
    private val timestamp = 1_800_000_000_000L
    private val original = LocationCheckpoint(Coordinate(25.033123, 121.565444), timestamp)

    @Test fun coordinateAndTimestampRoundTrip() {
        assertEquals(original, CheckpointCodec.decode(CheckpointCodec.encode(original), timestamp + 1_000L))
    }

    @Test fun recentInterruptedSessionCanBeRecoveredAsPosition() {
        val parsed = CheckpointCodec.decode(CheckpointCodec.encode(original), timestamp + 1L)
        assertNotNull(parsed)
        assertEquals(original.coordinate, parsed?.coordinate)
    }

    @Test fun rejectsExpiredCheckpoint() {
        assertNull(CheckpointCodec.decode(CheckpointCodec.encode(original),
            timestamp + CheckpointCodec.MAX_AGE_MS + 1L))
    }

    @Test fun rejectsFutureCheckpointAndClockSkew() {
        assertNull(CheckpointCodec.decode(CheckpointCodec.encode(original), timestamp - 1L))
    }

    @Test fun acceptsExactlyTwelveHoursOldCheckpoint() {
        assertEquals(original, CheckpointCodec.decode(CheckpointCodec.encode(original),
            timestamp + CheckpointCodec.MAX_AGE_MS))
    }

    @Test fun rejectsMalformedOrWrongVersion() {
        val bad = listOf(
            null, "", "v2|25|121|$timestamp", "v1|25|121",
            "v1|25|121|$timestamp|extra", "v1|NaN|121|$timestamp",
            "v1|999|121|$timestamp", "v1|25|181|$timestamp",
            "v1|25|121|not-time", "v1|25|121|0",
        )
        bad.forEach { assertNull("Invalid value must be rejected: $it", CheckpointCodec.decode(it, timestamp)) }
    }
}
