package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class CoordinateTest {
    @Test fun parsesValidTaiwanCoordinates() {
        assertEquals(Coordinate(25.033, 121.565), Coordinate.parse(" 25.033 ", "121.565"))
    }

    @Test fun rejectsInvalidCoordinates() {
        assertNull(Coordinate.parse("100.01", "121"))
        assertNull(Coordinate.parse("25", "200"))
        assertNull(Coordinate.parse("NaN", "121"))
        assertNull(Coordinate.parse("", ""))
    }

    @Test fun formatsStableDecimalCoordinate() {
        assertEquals("25.033000, 121.565000", Coordinate(25.033, 121.565).label())
    }
}
