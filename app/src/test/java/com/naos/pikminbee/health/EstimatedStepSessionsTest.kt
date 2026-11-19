package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class EstimatedStepSessionsTest {
    @Test fun pendingVirtualEstimateRoundTripsWithStableRecordId() {
        val session = EstimatedStepSession("virtual-abc", 1_000L, 15_000L, 170L)
        val decoded = EstimatedStepSessionCodec.decode(EstimatedStepSessionCodec.encode(listOf(session)))
        assertEquals(listOf(session), decoded)
        assertFalse(decoded.single().synced)
    }

    @Test fun duplicateClientIdsAreIgnoredWhilePreservingFirstRecord() {
        val first = EstimatedStepSession("same-id", 1_000L, 2_000L, 42L)
        val second = first.copy(steps = 300)
        val decoded = EstimatedStepSessionCodec.decode(
            EstimatedStepSessionCodec.encode(listOf(first, second)))
        assertEquals(1, decoded.size)
        assertEquals(42L, decoded.single().steps)
    }

    @Test fun invalidOrEmptyStepsMustNeverEnterHealthData() {
        assertThrows(IllegalArgumentException::class.java) {
            EstimatedStepSession("x", 1_000L, 3_000L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EstimatedStepSession("x", 10_000L, 2_000L, 120L)
        }
        assertTrue(EstimatedStepSessionCodec.decode("not-json").isEmpty())
    }
}
