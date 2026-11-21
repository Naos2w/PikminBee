package com.naos.pikminbee.route

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class FlowerOrderOptimiserTest {
    private val a = Coordinate(25.0330, 121.5654)
    private val b = Coordinate(25.0340, 121.5664)
    private val c = Coordinate(25.0350, 121.5674)

    private fun response(vararg indices: Int): String = JSONObject()
        .put("trip", JSONObject().put("locations", JSONArray().apply {
            indices.forEach { put(JSONObject().put("original_index", it)) }
        })).toString()

    @Test fun returnsOriginalCoordinatesInOptimisedWalkingOrder() {
        val stops = listOf(a,b,c)
        assertEquals(listOf(c,a,b), ValhallaPlanner.parseOptimisedOrder(
            response(0,3,1,2,4), stops, loop = true))
        assertEquals(listOf(b,a,c), ValhallaPlanner.parseOptimisedOrder(
            response(0,2,1,3), stops, loop = false))
    }

    @Test fun refusesDuplicateIndicesAndAlteredFixedAnchors() {
        val stops = listOf(a,b,c)
        assertThrows(ValhallaPlanner.RouteException::class.java) {
            ValhallaPlanner.parseOptimisedOrder(response(0,1,1,3,4), stops, loop = true)
        }
        assertThrows(ValhallaPlanner.RouteException::class.java) {
            ValhallaPlanner.parseOptimisedOrder(response(1,0,2,3,4), stops, loop = true)
        }
        assertThrows(ValhallaPlanner.RouteException::class.java) {
            ValhallaPlanner.parseOptimisedOrder("{}", stops, loop = true)
        }
    }
}
