package com.naos.pikminbee.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedWalkingRouteTest {
    private val origin = Coordinate(25.0, 121.0)
    private val destination = Coordinate(25.001, 121.001)
    private val prepared = PreparedWalkingRoute(origin, listOf(destination), listOf(origin, destination))

    @Test fun preservesPreviewAcrossOrdinaryGpsJitter() {
        assertTrue(prepared.isCurrent(Coordinate(25.00003, 121.00002), listOf(destination)))
    }

    @Test fun staleStartKeepsDrawingButCannotStart() {
        assertTrue(prepared.matchesDraft(listOf(destination), false))
        assertFalse(prepared.isCurrent(Coordinate(25.001, 121.0), listOf(destination), false))
        assertFalse(prepared.matchesDraft(listOf(destination), true))
        assertFalse(prepared.matchesDraft(listOf(origin), false))
    }

    @Test fun changingStopOrMovingStartRequiresNewPlan() {
        assertFalse(prepared.isCurrent(origin, listOf(Coordinate(25.002, 121.002))))
        assertFalse(prepared.isCurrent(Coordinate(25.001, 121.0), listOf(destination)))
        assertFalse(prepared.isCurrent(null, listOf(destination)))
    }
}
