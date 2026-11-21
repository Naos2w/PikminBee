package com.naos.pikminbee.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualEstimateSyncPolicyTest {
    @Test fun checkpointRequiresOptInAndAnElapsedMinute() {
        val start = 10_000L
        assertFalse(VirtualEstimateSyncPolicy.shouldCheckpoint(start, 70_000L, false))
        assertFalse(VirtualEstimateSyncPolicy.shouldCheckpoint(start, 69_999L, true))
        assertTrue(VirtualEstimateSyncPolicy.shouldCheckpoint(start, 70_000L, true))
        assertFalse(VirtualEstimateSyncPolicy.shouldCheckpoint(0, 70_000L, true))
        assertFalse(VirtualEstimateSyncPolicy.shouldCheckpoint(70_000L, 69_000L, true))
    }

    @Test fun savedCheckpointAdvancesBaselineWithoutRecounting() {
        assertEquals(314L, VirtualEstimateSyncPolicy.unsavedSteps(314, 0))
        assertEquals(0L, VirtualEstimateSyncPolicy.unsavedSteps(314, 314))
        assertEquals(41L, VirtualEstimateSyncPolicy.unsavedSteps(355, 314))
        assertEquals(0L, VirtualEstimateSyncPolicy.unsavedSteps(290, 314))
    }
}
