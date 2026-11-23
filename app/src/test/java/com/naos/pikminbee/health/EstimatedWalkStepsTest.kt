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

    @Test fun strideChangePreservesAllPreviouslyEstimatedSteps() {
        val counter = EstimatedStepCounter()
        counter.addTravel(72.0)
        val before = counter.snapshot().estimatedSteps
        assertEquals(100L, before)
        counter.setStrideForNextTravel(1.0)
        assertEquals(before, counter.snapshot().estimatedSteps)
        counter.addTravel(10.0)
        assertEquals(before + 10L, counter.snapshot().estimatedSteps)
    }

    @Test fun newCounterPreservesCountAndUnconvertedDistanceAcrossRestart() {
        val original = EstimatedStepCounter()
        original.addTravel(50.0)
        original.setStrideForNextTravel(0.9)
        original.addTravel(23.0)
        val restored = EstimatedStepCounter()
        restored.restore(original.snapshot().totalMetres, original.snapshot().estimatedSteps,
            original.storedRemainder(), original.storedStride())
        assertEquals(original.snapshot(), restored.snapshot())
        assertEquals(original.addTravel(4.5).estimatedSteps,
            restored.addTravel(4.5).estimatedSteps)
    }

    @Test fun legacyDistanceOnlyRestoreKeepsOriginalDefaultStride() {
        val counter = EstimatedStepCounter()
        counter.restore(72.0)
        assertEquals(100L, counter.snapshot().estimatedSteps)
        assertEquals(0.72, counter.storedStride(), 1e-12)
    }

    @Test fun fractionalTickRoundingNeverLosesAnEntireStep() {
        val counter = EstimatedStepCounter()
        repeat(40) { counter.addTravel(0.72) }
        assertEquals(40L, counter.snapshot().estimatedSteps)
    }

    @Test fun configuredStrideIsBoundedAndDefaultsWhenStoredInputIsMalformed() {
        assertEquals(0.72, VirtualStridePolicy.fromSaved(null), 0.0)
        assertEquals(0.72, VirtualStridePolicy.fromSaved(Double.NaN), 0.0)
        assertEquals(0.72, VirtualStridePolicy.fromSaved(0.72f.toDouble()), 0.0)
        assertEquals(0.72, VirtualStridePolicy.fromSaved(-1.0), 0.0)
        assertEquals(0.9, VirtualStridePolicy.fromSaved(0.9), 0.0)
        val counter = EstimatedStepCounter()
        assertThrows(IllegalArgumentException::class.java) {
            counter.setStrideForNextTravel(2.0)
        }
    }

    @Test fun invalidTravelIsRejected() {
        val tracker = EstimatedStepCounter()
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(Double.NaN) }
    }
}
