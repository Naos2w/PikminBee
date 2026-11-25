package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test

class RouteDraftRevisionTest {
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
