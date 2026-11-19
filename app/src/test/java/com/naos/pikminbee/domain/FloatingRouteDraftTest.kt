package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class FloatingRouteDraftTest {
    private val current = Coordinate(25.033, 121.5654)
    private val first = Coordinate(25.034, 121.566)
    private val second = Coordinate(25.035, 121.567)

    @Test fun launchesAtCurrentPositionInsteadOfTeleportingToFirstWaypoint() {
        val points = FloatingRouteDraft.anchors(current, listOf(first, second), false)
        assertEquals(listOf(current, first, second), points)
    }

    @Test fun flowerCycleReturnsToOrigin() {
        val points = FloatingRouteDraft.anchors(current, listOf(first, second), true)
        assertEquals(listOf(current, first, second, current), points)
    }

    @Test fun requiresARealCurrentOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            FloatingRouteDraft.anchors(null, listOf(first), false)
        }
    }

    @Test fun ignoresOverlappingWaypointsRatherThanTeleporting() {
        assertEquals(listOf(current, first),
            FloatingRouteDraft.anchors(current, listOf(current, first), false))
    }
}
