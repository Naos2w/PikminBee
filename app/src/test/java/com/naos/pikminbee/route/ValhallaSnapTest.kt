package com.naos.pikminbee.route

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaSnapTest {
    private val picked = listOf(Coordinate(25.0, 121.0), Coordinate(25.001, 121.001))

    @Test fun nearbyFootpathsAreAcceptedWithoutExactRoadTaps() {
        // Both snapped positions are roughly 89 m from the original taps.
        val json = """{"trip":{"legs":[{"shape":"_u|tn@_cgxeFo}@o}@"}]}}"""
        val road = ValhallaPlanner.parseResponse(json, picked)
        assertEquals(2, road.size)
        assertTrue(WalkingGeometry.distanceMeters(road.first(), picked.first()) > 45.0)
        assertTrue(WalkingGeometry.distanceMeters(road.first(), picked.first()) < ValhallaPlanner.MAX_SNAP_METERS)
    }

    @Test fun distantSnapIsStillRejectedInsteadOfSilentlyMovingTheRoute() {
        // Both snapped positions are more than 300 m from the chosen points.
        val json = """{"trip":{"legs":[{"shape":"o~`un@_cgxeFo}@o}@"}]}}"""
        val error = runCatching { ValhallaPlanner.parseResponse(json, picked) }.exceptionOrNull()
        assertTrue(error is ValhallaPlanner.RouteException)
        assertTrue(error!!.message!!.contains("200"))
    }
}
