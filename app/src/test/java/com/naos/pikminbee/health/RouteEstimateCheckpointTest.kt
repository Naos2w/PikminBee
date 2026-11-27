package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class RouteEstimateCheckpointTest {
    @Test fun savesEachMinuteDuringMovementWithoutRepeatingCounts() {
        val checkpoints = RouteEstimateCheckpoint()
        val saved = mutableListOf<RouteEstimateCheckpoint.Chunk>()
        for (i in 1L..120L) {
            checkpoints.recordMovement(10_000L + (i - 1) * 1000L,
                10_000L + i * 1000L, i * 1000L, 2)
            checkpoints.drain { saved.add(it); true }
            if (i == 59L) assertTrue(saved.isEmpty())
            if (i == 60L) assertEquals(1, saved.size)
        }
        checkpoints.finish()
        checkpoints.drain { saved.add(it); true }
        assertEquals(listOf(
            RouteEstimateCheckpoint.Chunk(10_000, 70_000, 120),
            RouteEstimateCheckpoint.Chunk(70_000, 130_000, 120),
        ), saved)
    }

    @Test fun pauseAndResumeNeverIncludePausedTimeEvenIfLocalSaveFails() {
        val checkpoints = RouteEstimateCheckpoint()
        checkpoints.recordMovement(10_000, 11_000, 1000, 3)
        checkpoints.finish()
        assertEquals(0, checkpoints.drain { false })
        checkpoints.recordMovement(31_000, 32_000, 22_000, 2)
        checkpoints.finish()
        val saved = mutableListOf<RouteEstimateCheckpoint.Chunk>()
        assertEquals(2, checkpoints.drain { saved.add(it); true })
        assertEquals(listOf(
            RouteEstimateCheckpoint.Chunk(10_000, 11_000, 3),
            RouteEstimateCheckpoint.Chunk(31_000, 32_000, 2),
        ), saved)
        assertEquals(0, checkpoints.drain { fail("duplicate"); false })
    }

    @Test fun routeMovementRecordsImmediatelyWithoutAnyAppPreference() {
        val checkpoints = RouteEstimateCheckpoint()
        checkpoints.recordMovement(10_000, 11_000, 1000, 2)
        checkpoints.recordMovement(11_000, 12_000, 2000, 3)
        checkpoints.recordMovement(12_000, 13_000, 3000, 4)
        checkpoints.recordMovement(13_000, 14_000, 4000, 5)
        checkpoints.finish()
        val saved = mutableListOf<RouteEstimateCheckpoint.Chunk>()
        checkpoints.drain { saved.add(it); true }
        assertEquals(listOf(RouteEstimateCheckpoint.Chunk(10_000, 14_000, 14)), saved)
    }

    @Test fun zeroMovementAndRepeatedFinishDoNotCreateRecords() {
        val checkpoints = RouteEstimateCheckpoint()
        checkpoints.recordMovement(10_000, 11_000, 1000, 0)
        checkpoints.finish()
        checkpoints.finish()
        assertEquals(0, checkpoints.drain { fail("unexpected record"); false })
    }

    @Test fun longGapDoesNotBecomeActiveWalkingTime() {
        val checkpoints = RouteEstimateCheckpoint()
        checkpoints.recordMovement(10_000, 11_000, 1000, 2)
        checkpoints.recordMovement(90_000, 91_000, 81_000, 3)
        checkpoints.finish()
        val saved = mutableListOf<RouteEstimateCheckpoint.Chunk>()
        checkpoints.drain { saved.add(it); true }
        assertEquals(listOf(
            RouteEstimateCheckpoint.Chunk(10_000, 11_000, 2),
            RouteEstimateCheckpoint.Chunk(90_000, 91_000, 3),
        ), saved)
    }
}
