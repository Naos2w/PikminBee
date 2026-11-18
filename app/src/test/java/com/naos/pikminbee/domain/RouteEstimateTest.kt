package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class RouteEstimateTest {
    @Test fun exactRouteLengthAndSpeedGiveExpectedTime() {
        val estimate = RouteEstimate.fromDistance(1_400.0, WalkSpeedPreset.WALK)
        assertEquals("1.40 公里", estimate.distanceLabel)
        assertEquals(60L, estimate.minutes)
        assertEquals("1 小時", estimate.durationLabel)
    }

    @Test fun speedSelectionRecalculatesWithoutReplanning() {
        val metres = 1_800.0
        assertEquals(135L, RouteEstimate.fromDistance(metres, WalkSpeedPreset.SLOW).minutes)
        assertEquals(78L, RouteEstimate.fromDistance(metres, WalkSpeedPreset.WALK).minutes)
        assertEquals(10L, RouteEstimate.fromDistance(metres, WalkSpeedPreset.FAST).minutes)
        assertEquals("2 小時 15 分鐘",
            RouteEstimate.fromDistance(metres, WalkSpeedPreset.SLOW).durationLabel)
    }

    @Test fun geometryUsesEveryRoadSegmentRatherThanStopToStopAirline() {
        val a = Coordinate(25.0330, 121.5654)
        val b = Coordinate(25.0340, 121.5654)
        val c = Coordinate(25.0340, 121.5664)
        val estimate = RouteEstimate.fromRoad(listOf(a, b, c), WalkSpeedPreset.WALK)
        assertEquals(WalkingGeometry.distanceMeters(a, b) + WalkingGeometry.distanceMeters(b, c),
            estimate.distanceMetres, 0.01)
        assertTrue(estimate.distanceMetres > WalkingGeometry.distanceMeters(a, c))
    }

    @Test fun smallDistancesRoundUpButZeroDoesNot() {
        assertEquals("75 公尺", RouteEstimate.fromDistance(75.0, WalkSpeedPreset.WALK).distanceLabel)
        assertEquals(4L, RouteEstimate.fromDistance(75.0, WalkSpeedPreset.WALK).minutes)
        assertEquals(0L, RouteEstimate.fromDistance(0.0, WalkSpeedPreset.WALK).minutes)
    }

    @Test fun invalidDistancesAndGeometryAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteEstimate.fromDistance(-1.0, WalkSpeedPreset.WALK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteEstimate.fromRoad(listOf(Coordinate(25.0, 121.0)), WalkSpeedPreset.WALK)
        }
    }
}
