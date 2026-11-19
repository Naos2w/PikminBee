package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class PhysicalStepCounterTest {
    @Test fun firstReadingBecomesBaselineNotLifetimeSteps() {
        val sensor = PhysicalStepCounter()
        assertEquals(0L, sensor.observe(15_300f, 10_000_000_000L))
        assertEquals(12L, sensor.observe(15_312f, 15_000_000_000L))
    }

    @Test fun rebootOrSensorRestartDoesNotFabricateSteps() {
        val sensor = PhysicalStepCounter()
        sensor.observe(9_000f, 50_000_000_000L)
        assertEquals(0L, sensor.observe(20f, 3_000_000_000L))
        assertEquals(7L, sensor.observe(27f, 7_000_000_000L))
    }

    @Test fun spikesAreNotTreatedAsActualWalking() {
        val sensor = PhysicalStepCounter()
        sensor.observe(100f, 20_000_000_000L)
        assertEquals(0L, sensor.observe(10_000f, 21_000_000_000L))
        assertEquals(5L, sensor.observe(10_005f, 24_000_000_000L))
    }

    @Test fun duplicateAndInvalidSamplesAreIgnored() {
        val sensor = PhysicalStepCounter()
        assertEquals(0L, sensor.observe(Float.NaN, 1L))
        assertEquals(0L, sensor.observe(40f, 10_000_000_000L))
        assertEquals(0L, sensor.observe(40f, 12_000_000_000L))
        assertEquals(0L, sensor.observe(50f, 12_000_000_000L))
        assertEquals(3L, sensor.observe(53f, 14_000_000_000L))
    }
}
