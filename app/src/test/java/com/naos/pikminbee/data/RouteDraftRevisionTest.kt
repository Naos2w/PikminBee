package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test

class RouteDraftRevisionTest {
    @Test fun routeOptionsAndCancellationInvalidateOldOwners() {
        val old = RouteDraftState.snapshot()
        val origin = Coordinate(20.0, 120.0)
        val stops = listOf(Coordinate(20.01, 120.01))
        try {
            RouteDraftState.publish(stops, origin, true)
            val draft = RouteDraftState.snapshot()
            assertEquals(origin, draft.origin)
            assertTrue(draft.loop)
            val first = RouteDraftState.beginPlanning(draft.revision)!!
            assertTrue(RouteDraftState.cancelPlanning())
            assertFalse(RouteDraftState.isPlanning(first))
            val second = RouteDraftState.beginPlanning(draft.revision)!!
            RouteDraftState.finishPlanning(first, RoutePlanningNotice("old error", true))
            assertTrue(RouteDraftState.isPlanning(second))
            assertFalse(RouteDraftState.planningNotice.value.error)
            RouteDraftState.finishPlanning(second, RoutePlanningNotice("new error", true))
            assertEquals("new error", RouteDraftState.planningNotice.value.text)
            RouteDraftState.publish(stops, origin, false)
            assertFalse(RouteDraftState.canCommit(draft.revision))
            assertTrue(RouteDraftState.planningNotice.value.text.isEmpty())
        } finally {
            RouteDraftState.cancelPlanning()
            RouteDraftState.publish(old.stops, old.origin, old.loop)
        }
    }

    @Test fun firstPointConversionAdvancesDraftRevisionAndInvalidatesRoadPreview() {
        val old = RouteDraftState.snapshot()
        val oldPreview = PreparedRouteState.route.value
        val origin = Coordinate(20.0, 120.0)
        val first = Coordinate(20.01, 120.01)
        val second = Coordinate(20.02, 120.02)
        try {
            RouteDraftState.publish(listOf(first, second), origin, true)
            val before = RouteDraftState.snapshot()
            PreparedRouteState.route.value = com.naos.pikminbee.domain.PreparedWalkingRoute(
                origin, before.stops, listOf(origin,first,second,origin), true)
            val next = com.naos.pikminbee.domain.RoutePointDraft.promoteFirst(before.stops)!!
            RouteDraftState.publish(next.stops, next.origin, before.loop)
            val after = RouteDraftState.snapshot()
            assertEquals(first, after.origin)
            assertEquals(listOf(second), after.stops)
            assertTrue(after.loop)
            assertEquals(before.revision + 1, after.revision)
            assertFalse(RouteDraftState.canCommit(before.revision))
            assertNull(PreparedRouteState.route.value)
        } finally {
            RouteDraftState.publish(old.stops, old.origin, old.loop)
            PreparedRouteState.route.value = oldPreview
        }
    }

    @Test fun savedRouteRetainsExplicitOriginLoopAndOrderedStops() {
        val origin = Coordinate(20.0, 120.0)
        val stops = listOf(Coordinate(20.01, 120.01), Coordinate(20.02, 120.02))
        val route = SavedRoute.create("Walk", com.naos.pikminbee.domain.WalkMode.ROUTE, stops, origin, true)
        assertEquals(origin, route.origin)
        assertTrue(route.loop)
        assertEquals(stops, route.stops)
        assertEquals(origin, route.copy(name = "Renamed").origin)
        val legacy = SavedRoute.create("Legacy", com.naos.pikminbee.domain.WalkMode.ROUTE, stops)
        assertNull(legacy.origin)
        assertFalse(legacy.loop)
    }

    @Test fun revisionsAndPlanningOwnershipRejectStaleOrConcurrentEdits() {
        val old = RouteDraftState.snapshot().stops
        val initial = listOf(Coordinate(20.1, 120.1), Coordinate(20.2, 120.2))
        var owner: Long? = null
        try {
            RouteDraftState.publish(initial)
            val snapshot = RouteDraftState.snapshot()
            assertTrue(RouteDraftState.canCommit(snapshot.revision))
            owner = RouteDraftState.beginPlanning(snapshot.revision)
            assertNotNull(owner)
            assertFalse(RouteDraftState.canCommit(snapshot.revision))
            assertNull(RouteDraftState.beginPlanning(snapshot.revision))
            RouteDraftState.finishPlanning(owner!! + 100)
            assertTrue(RouteDraftState.planning.value)
            RouteDraftState.finishPlanning(owner!!)
            assertTrue(RouteDraftState.canCommit(snapshot.revision))
            RouteDraftState.publish(initial.reversed())
            assertFalse(RouteDraftState.canCommit(snapshot.revision))
            assertEquals(snapshot.revision + 1, RouteDraftState.snapshot().revision)
            RouteDraftState.publish(initial.reversed())
            assertEquals(snapshot.revision + 1, RouteDraftState.snapshot().revision)
            RouteDraftState.publish(initial)
            assertFalse(RouteDraftState.canCommit(snapshot.revision))
            assertNull(RouteDraftState.beginPlanning(snapshot.revision))
        } finally {
            owner?.let { RouteDraftState.finishPlanning(it) }
            RouteDraftState.publish(old)
        }
    }
}
