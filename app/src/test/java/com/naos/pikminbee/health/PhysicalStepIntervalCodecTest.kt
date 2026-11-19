package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class PhysicalStepIntervalCodecTest {
    @Test fun realSensorRecordRoundTripsAndKeepsUniqueId() {
        val item = PhysicalStepInterval("sensor-1", 1_000, 61_000, 83)
        assertEquals(listOf(item), PhysicalStepIntervalCodec.decode(
            PhysicalStepIntervalCodec.encode(listOf(item))))
    }

    @Test fun repeatedRecordIdDoesNotBecomeTwoHCRecords() {
        val a = PhysicalStepInterval("same", 1_000, 2_000, 18)
        val b = a.copy(steps = 22)
        val decoded = PhysicalStepIntervalCodec.decode(
            PhysicalStepIntervalCodec.encode(listOf(a, b)))
        assertEquals(1, decoded.size)
        assertEquals(18L, decoded.single().steps)
    }

    @Test fun rejectsFabricatedOrZeroStepIntervals() {
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalStepInterval("x", 1_000, 2_000, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhysicalStepInterval("x", 2_000, 1_000, 12)
        }
        assertTrue(PhysicalStepIntervalCodec.decode("not JSON").isEmpty())
    }
}
