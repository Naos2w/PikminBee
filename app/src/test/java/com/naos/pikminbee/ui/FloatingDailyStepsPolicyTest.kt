package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingDailyStepsPolicyTest {
    @Test fun idleHasNoHealthTotal() {
        assertEquals("", FloatingDailyStepsPolicy.render(null))
    }

    @Test fun hcAndSensorAreNeverSummedAndEstimateRemainsSeparate() {
        assertEquals("今日總步數 1,000 步（HC 彙總）\n本次路線估算 1,250 步（非實際運動）",
            FloatingDailyStepsPolicy.render(1250, true, 1000, 900))
        assertEquals("今日已記錄 900 步（手機）",
            FloatingDailyStepsPolicy.render(null, true, null, 900))
    }

    @Test fun optInIsIndependentOfHealthAvailability() {
        assertEquals("", FloatingDailyStepsPolicy.render(null, false, 1000, 900))
        assertEquals("今日步數 —（尚未取得）",
            FloatingDailyStepsPolicy.render(null, true, null, null))
        assertEquals("今日總步數 0 步（HC 彙總）",
            FloatingDailyStepsPolicy.render(null, true, 0, 900))
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
