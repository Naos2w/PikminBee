package com.naos.pikminbee.route

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.PreparedWalkingRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadSnapDetailsTest {
    @Test fun reportedSnapOffsetsTriggerExplicitStartConfirmation() {
        val picked = listOf(Coordinate(25.0, 121.0), Coordinate(25.001, 121.001))
        val json = """{"trip":{"legs":[{"shape":"_u|tn@_cgxeFo}@o}@"}]}}"""
        val detailed = ValhallaPlanner.parseDetailedResponse(json, picked)
        assertEquals(2, detailed.snapMetres.size)
        assertTrue(detailed.originSnapMetres > 40.0)
        assertTrue(detailed.maxSnapMetres <= ValhallaPlanner.MAX_SNAP_METERS)
        val prepared = PreparedWalkingRoute(
            picked.first(), listOf(picked.last()), detailed.geometry,
            snapMetres = detailed.snapMetres,
        )
        assertTrue(prepared.requiresSnapConfirmation)
    }

    @Test fun flowerLoopChangeInvalidatesPreparedPreview() {
        val start = Coordinate(25.0, 121.0)
        val stop = Coordinate(25.001, 121.001)
        val prepared = PreparedWalkingRoute(
            start, listOf(stop), listOf(start, stop),
            loop = true,
        )
        assertTrue(prepared.isCurrent(start, listOf(stop), true))
        assertFalse(prepared.isCurrent(start, listOf(stop), false))
    }
}
