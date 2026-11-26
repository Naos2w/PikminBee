package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingDailyStepsPolicyTest {
    @Test fun idleHasNoHealthTotal() {
        assertEquals("", FloatingDailyStepsPolicy.render(null))
    }

    @Test fun routeEstimateIsExplicitlyNotExercise() {
        assertEquals("本次路線估算 1,250 步（非實際運動）",
            FloatingDailyStepsPolicy.render(1250))
    }

    @Test fun nonNegativeEstimate() {
        assertEquals("本次路線估算 0 步（非實際運動）",
            FloatingDailyStepsPolicy.render(-1))
    }
}
