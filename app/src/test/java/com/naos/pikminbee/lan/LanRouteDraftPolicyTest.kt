package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import org.junit.Assert.*
import org.junit.Test

class LanRouteDraftPolicyTest {
    private val start = Coordinate(25.0330, 121.5654)
    private val one = Coordinate(25.0340, 121.5654)
    private val two = Coordinate(25.0350, 121.5654)

    @Test fun remoteFlowerRequiresExplicitCurrentMockOrigin() {
        val result = LanRouteDraftPolicy.add(WalkMode.FLOWERS, emptyList(), one, null)
        assertFalse(result.accepted)
        assertEquals(emptyList<Coordinate>(), result.stops)
    }

    @Test fun remoteFlowerUsesSameDuplicateAndOriginGuardsAsPhone() {
        val initial = LanRouteDraftPolicy.add(WalkMode.FLOWERS, emptyList(), one, start)
        assertTrue(initial.accepted)
        val duplicate = LanRouteDraftPolicy.add(WalkMode.FLOWERS, initial.stops, one, start)
        assertFalse(duplicate.accepted)
        assertEquals(initial.stops, duplicate.stops)
        val nearStart = LanRouteDraftPolicy.add(WalkMode.FLOWERS, initial.stops, start, start)
        assertFalse(nearStart.accepted)
        assertEquals(initial.stops, nearStart.stops)
    }

    @Test fun remoteFlowerHasFourteenStopCapEvenForManualRequests() {
        val points = (1..14).map { Coordinate(25.034 + it * .001, 121.5654) }
        val result = LanRouteDraftPolicy.add(WalkMode.FLOWERS, points, two, start)
        assertFalse(result.accepted)
        assertEquals(points, result.stops)
        assertEquals(16, LanRouteDraftPolicy.anchors(WalkMode.FLOWERS, start, points, true).size)
    }

    @Test fun routeModeAllowsFifteenStopsAndRejectsSixteenth() {
        val points = (1..15).map { Coordinate(25.034 + it * .001, 121.5654) }
        val rejected = LanRouteDraftPolicy.add(WalkMode.ROUTE, points, one, null)
        assertFalse(rejected.accepted)
        assertEquals(points, rejected.stops)
        val added = LanRouteDraftPolicy.add(WalkMode.ROUTE, points.dropLast(1), one, null)
        assertTrue(added.accepted)
        assertEquals(15, added.stops.size)
        assertEquals(16, LanRouteDraftPolicy.anchors(WalkMode.ROUTE, start, points, false).size)
    }

    @Test fun loopLimitsRouteAnchorsBeforeValhallaRequest() {
        val stops = (1..15).map { Coordinate(25.034 + it * .001, 121.5654) }
        assertThrows(IllegalArgumentException::class.java) {
            LanRouteDraftPolicy.anchors(WalkMode.ROUTE, start, stops, true)
        }
        assertEquals(16, LanRouteDraftPolicy.anchors(
            WalkMode.ROUTE, start, stops.dropLast(1), true).size)
    }

    @Test fun remoteReorderHasStableIndicesAndRejectsBoundaryEdits() {
        val initial = listOf(start, one, two)
        val moved = LanRouteDraftPolicy.reorder(initial, 1, 1)
        assertTrue(moved.accepted)
        assertEquals(listOf(start, two, one), moved.stops)
        assertEquals(initial, LanRouteDraftPolicy.reorder(initial, 0, -1).stops)
        assertFalse(LanRouteDraftPolicy.reorder(initial, 0, -1).accepted)
        assertEquals(initial, LanRouteDraftPolicy.reorder(initial, 0, 2).stops)
        assertEquals(initial, LanRouteDraftPolicy.reorder(initial, 99, 1).stops)
        assertEquals(initial, LanRouteDraftPolicy.remove(initial, 9).stops)
        val removed = LanRouteDraftPolicy.remove(initial, 1)
        assertTrue(removed.accepted)
        assertEquals(listOf(start, two), removed.stops)
        assertEquals(initial, listOf(start, one, two)) // Source draft remains immutable.
    }

    @Test fun remoteDragCanCrossMultipleRowsAndRejectInvalidTargets() {
        val initial = listOf(start, one, two)
        assertEquals(listOf(one, two, start), LanRouteDraftPolicy.moveTo(initial, 0, 2).stops)
        assertTrue(LanRouteDraftPolicy.moveTo(initial, 2, 0).accepted)
        assertEquals(listOf(two, start, one), LanRouteDraftPolicy.moveTo(initial, 2, 0).stops)
        assertFalse(LanRouteDraftPolicy.moveTo(initial, 0, 3).accepted)
        assertEquals(initial, LanRouteDraftPolicy.moveTo(initial, -1, 2).stops)
    }

    @Test fun remoteFlowerLoopKeepsOriginalStartWithoutStraightLineFallback() {
        val stops = listOf(one, two)
        assertEquals(listOf(start, one, two, start),
            LanRouteDraftPolicy.anchors(WalkMode.FLOWERS, start, stops, true))
    }

    @Test fun zeroLengthRoutesAreRejectedBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            LanRouteDraftPolicy.anchors(WalkMode.ROUTE, start, listOf(start), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanRouteDraftPolicy.anchors(WalkMode.FLOWERS, start, emptyList(), true)
        }
    }
}
