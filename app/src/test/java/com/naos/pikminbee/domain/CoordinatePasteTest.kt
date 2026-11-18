package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class CoordinatePasteTest {
    @Test fun acceptsCommaAndWhitespaceSeparatedDecimalDegrees() {
        val point = Coordinate(25.033, 121.5654)
        assertEquals(point, Coordinate.parseCombined("25.0330, 121.5654"))
        assertEquals(point, Coordinate.parseCombined("  25.0330  121.5654 "))
        assertEquals(point, Coordinate.parseCombined("25.0330，121.5654"))
        assertEquals(Coordinate(-33.8688, 151.2093), Coordinate.parseCombined("-33.8688, +151.2093"))
    }

    @Test fun rejectsAddressesIncompleteAndOutsideWorldCoordinates() {
        listOf("", "台北市政府", "25.033", "25.033, 121.5654, 0",
               "95.3, 121.5", "25.0, 191.0", "NaN, 121.5", "Infinity, 121.5")
            .forEach { assertNull(it, Coordinate.parseCombined(it)) }
    }
}
