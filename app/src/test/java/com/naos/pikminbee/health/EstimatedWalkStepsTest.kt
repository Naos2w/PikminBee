package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class EstimatedWalkStepsTest {
    @Test fun oneHundredMetresAreEstimatedNotMeasuredSteps() {
        val tracker = EstimatedStepCounter()
        assertEquals(138L, tracker.addTravel(100.0).estimatedSteps)
    }
    @Test fun distanceAccumulatesAcrossSmallMovingTicks() {
        val tracker = EstimatedStepCounter()
        repeat(72) { tracker.addTravel(.5) }
        assertEquals(50L, tracker.snapshot().estimatedSteps)
    }
    @Test fun pauseDoesNotGenerateAnyStepsWithoutTravel() {
        val tracker = EstimatedStepCounter()
        tracker.addTravel(7.2)
        assertEquals(10L, tracker.addTravel(0.0).estimatedSteps)
    }
    @Test fun increasingSelectedSpeedIncreasesStepsForSameElapsedTime() {
        val slow = EstimatedStepCounter()
        val faster = EstimatedStepCounter()
        repeat(60) {
            slow.addTravel(0.8 / 3.6)
            faster.addTravel(1.4 / 3.6)
        }
        assertTrue(faster.snapshot().estimatedSteps > slow.snapshot().estimatedSteps)
    }
    @Test fun currentWalkStepsFreezeOnPauseStopAndResetForNextWalk() {
        val tracker = EstimatedStepCounter()
        tracker.restore(360.0)
        val originalSteps = tracker.snapshot().estimatedSteps
        tracker.addTravel(7.2)
        val movingSteps = tracker.estimatedSince(originalSteps)
        assertTrue(movingSteps > 0L)
        // No added distance when the service is paused or parked.
        tracker.addTravel(0.0)
        assertEquals(movingSteps, tracker.estimatedSince(originalSteps))
        val nextWalkBaseline = tracker.snapshot().estimatedSteps
        assertEquals(0L, tracker.estimatedSince(nextWalkBaseline))
        tracker.addTravel(3.6)
        assertTrue(tracker.estimatedSince(nextWalkBaseline) > 0)
    }

    @Test fun invalidTravelIsRejected() {
        val tracker = EstimatedStepCounter()
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(Double.NaN) }
    }
}
