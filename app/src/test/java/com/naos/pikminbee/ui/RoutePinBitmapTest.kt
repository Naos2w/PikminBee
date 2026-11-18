package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutePinBitmapTest {
    @Test fun numberedPinsMarkStartIntermediateAndDestination() {
        assertEquals(RoutePinBitmap.Role.START, RoutePinBitmap.role(0, 3))
        assertEquals(RoutePinBitmap.Role.VIA, RoutePinBitmap.role(1, 3))
        assertEquals(RoutePinBitmap.Role.END, RoutePinBitmap.role(2, 3))
    }

    @Test fun oneWaypointRemainsAStartNotAFakeDestination() {
        assertEquals(RoutePinBitmap.Role.START, RoutePinBitmap.role(0, 1))
    }

    @Test fun invalidIndexesDoNotSilentlyCreateBadLabels() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutePinBitmap.role(3, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutePinBitmap.role(0, 0)
        }
    }
}
