package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDailyStepsPolicyTest {
    @Test fun healthConnectDailyIsPrimaryAndIsNotAddedToVirtualOrSensor() {
        val shown = FloatingDailyStepsPolicy.render(8_532, 4_100, 1_250)
        assertTrue(shown.startsWith("今日總步數 8,532 步（HC 彙總）"))
        assertTrue(shown.contains("本次虛擬估算 1,250 步"))
        assertFalse(shown.contains("4,100"))
        assertFalse(shown.contains("9,782"))
    }

    @Test fun validZeroFromHealthConnectIsShownNotTreatedAsMissing() {
        assertEquals("今日總步數 0 步（HC 彙總）",
            FloatingDailyStepsPolicy.render(0, 99, null))
    }

    @Test fun localSensorFallbackDoesNotClaimFullDayAggregate() {
        assertEquals("今日已記錄 358 步（手機）",
            FloatingDailyStepsPolicy.render(null, 358, null))
    }

    @Test fun unavailableDailyCountDoesNotUseHistoricalVirtualWalkAsDaily() {
        assertEquals("今日步數 —（尚未取得）\n本次虛擬估算 11 步",
            FloatingDailyStepsPolicy.render(null, null, 11))
    }

    @Test fun negativeOrUnavailableDataDoesNotBecomeTodayTotal() {
        assertEquals("今日步數 —（尚未取得）",
            FloatingDailyStepsPolicy.render(-1, -1, null))
    }

    @Test fun noWalkingSessionLeavesCompactSingleLine() {
        val shown = FloatingDailyStepsPolicy.render(2_000, null, null)
        assertFalse(shown.contains("\n"))
    }
}
