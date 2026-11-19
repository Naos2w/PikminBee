package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class RoutePointDraftTest {
    private val origin = Coordinate(25.033, 121.5654)
    private val first = Coordinate(25.034, 121.566)
    private val second = Coordinate(25.035, 121.567)
    private val replacement = Coordinate(25.036, 121.568)

    @Test fun tapImmediatelyAddsNextNumberedDestination() {
        val one = RoutePointDraft.select(emptyList(), origin, first)
        val two = RoutePointDraft.select(one.stops, origin, second)
        assertEquals(RoutePointDraft.Outcome.ADDED, one.outcome)
        assertEquals(0, one.index)
        assertEquals(1, two.index)
        assertEquals(listOf(first, second), two.stops)
    }

    @Test fun previousStepDiscardsLastDestinationForReselection() {
        val undone = RoutePointDraft.undo(listOf(first, second))
        assertEquals(listOf(first), undone)
        val next = RoutePointDraft.select(undone, origin, replacement)
        assertEquals(listOf(first, replacement), next.stops)
        assertEquals(1, next.index)
    }

    @Test fun tappingNumberedMarkerCanReplaceOrRemoveSpecificWaypoint() {
        val replaced = RoutePointDraft.select(listOf(first, second), origin, replacement, 0)
        assertEquals(RoutePointDraft.Outcome.REPLACED, replaced.outcome)
        assertEquals(listOf(replacement, second), replaced.stops)
        assertEquals(listOf(second), RoutePointDraft.remove(listOf(first, second), 0))
    }

    @Test fun duplicateAndOriginAreRejectedWithoutCreatingExtraPins() {
        assertEquals(RoutePointDraft.Outcome.DUPLICATE,
            RoutePointDraft.select(listOf(first), origin, first).outcome)
        assertEquals(RoutePointDraft.Outcome.TOO_CLOSE_TO_ORIGIN,
            RoutePointDraft.select(emptyList(), origin, origin).outcome)
    }

    @Test fun maxFourteenDestinationsAndInvalidEditAreSafe() {
        val stops = (1..14).map { Coordinate(25.0 + it * .001, 121.5 + it * .001) }
        val over = RoutePointDraft.select(stops, origin, replacement)
        assertEquals(RoutePointDraft.Outcome.FULL, over.outcome)
        assertEquals(stops, over.stops)
        val invalid = RoutePointDraft.select(listOf(first), origin, second, 9)
        assertEquals(RoutePointDraft.Outcome.INVALID_INDEX, invalid.outcome)
    }
}
