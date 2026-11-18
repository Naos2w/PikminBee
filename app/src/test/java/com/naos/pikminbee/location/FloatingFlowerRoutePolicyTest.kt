package com.naos.pikminbee.location

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkPhase
import org.junit.Assert.*
import org.junit.Test

class FloatingFlowerRoutePolicyTest {
    private val origin = Coordinate(25.0330, 121.5654)
    private val one = Coordinate(25.0337, 121.5660)
    private val two = Coordinate(25.0343, 121.5664)

    @Test fun requiresAnActiveMockPositionAndExplicitStops() {
        assertFalse(FloatingFlowerRoutePolicy.prepare(false, WalkPhase.IDLE, origin, listOf(one)).allowed)
        assertFalse(FloatingFlowerRoutePolicy.prepare(true, WalkPhase.IDLE, null, listOf(one)).allowed)
        assertFalse(FloatingFlowerRoutePolicy.prepare(true, WalkPhase.IDLE, origin, emptyList()).allowed)
    }

    @Test fun willNotReplaceMovingOrPausedRoute() {
        for (phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
            val d = FloatingFlowerRoutePolicy.prepare(true, phase, origin, listOf(one))
            assertFalse(d.allowed)
            assertTrue(d.anchors.isEmpty())
        }
    }

    @Test fun parkedOrIdleRouteIsExplicitCircularWalkingRequest() {
        for (phase in listOf(WalkPhase.IDLE, WalkPhase.PARKED)) {
            val d = FloatingFlowerRoutePolicy.prepare(true, phase, origin, listOf(one, two))
            assertTrue(d.allowed)
            assertNull(d.problem)
            assertEquals(listOf(origin, one, two, origin), d.anchors)
        }
    }

    @Test fun cannotCircleOnlyTheStartOrExceed14FlowerStops() {
        assertFalse(FloatingFlowerRoutePolicy.prepare(true, WalkPhase.PARKED, origin, listOf(origin)).allowed)
        val overLimit = (1..15).map { i -> Coordinate(25.035 + i * .001, 121.5654) }
        assertFalse(FloatingFlowerRoutePolicy.prepare(true, WalkPhase.IDLE, origin, overLimit).allowed)
    }

    @Test fun oneFlowerCreatesABackAndForthRoadRequestWithNoStraightLineFallback() {
        val d = FloatingFlowerRoutePolicy.prepare(true, WalkPhase.PARKED, origin, listOf(one))
        assertTrue(d.allowed)
        assertEquals(listOf(origin, one, origin), d.anchors)
        assertEquals(1.4, FloatingFlowerRoutePolicy.SPEED_METRES_PER_SECOND, 0.001)
    }
}
