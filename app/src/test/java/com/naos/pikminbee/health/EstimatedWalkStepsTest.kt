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
    @Test fun invalidTravelIsRejected() {
        val tracker = EstimatedStepCounter()
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { tracker.addTravel(Double.NaN) }
    }
}
