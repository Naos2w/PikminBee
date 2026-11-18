package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class FlowerMapDraftTest {
    private val origin = Coordinate(25.0330, 121.5654)
    private val one = Coordinate(25.0335, 121.5654)
    private val two = Coordinate(25.0341, 121.5661)

    @Test fun addsPickedPointWithoutChangingExplicitStart() {
        val first = FlowerMapDraft.add(emptyList(), origin, one)
        assertEquals(FlowerMapDraft.AddResult.ADDED, first.result)
        assertEquals(listOf(one), first.stops)
        assertEquals(listOf(origin, one), FlowerMapDraft.routePoints(origin, first.stops, false))
        assertEquals(listOf(origin, one, origin), FlowerMapDraft.routePoints(origin, first.stops, true))
    }

    @Test fun refusesDuplicateOrStartPointWithoutLosingStops() {
        val duplicate = FlowerMapDraft.add(listOf(one), origin, one)
        assertEquals(FlowerMapDraft.AddResult.DUPLICATE, duplicate.result)
        assertEquals(listOf(one), duplicate.stops)
        val atStart = FlowerMapDraft.add(listOf(one), origin, origin)
        assertEquals(FlowerMapDraft.AddResult.AT_START, atStart.result)
        assertEquals(listOf(one), atStart.stops)
    }

    @Test fun enforcesFourteenStopsAndPreservesExistingOrder() {
        val stops = (1..14).map { i -> Coordinate(25.034 + i * .001, 121.5654) }
        val result = FlowerMapDraft.add(stops, origin, two)
        assertEquals(FlowerMapDraft.AddResult.FULL, result.result)
        assertEquals(stops, result.stops)
        assertEquals(16, FlowerMapDraft.routePoints(origin, stops, true).size)
    }

    @Test fun allowsMovingAndRemovingIndividualStops() {
        val stops = listOf(one, two)
        assertEquals(listOf(two, one), FlowerMapDraft.move(stops, 0, 1))
        assertEquals(stops, FlowerMapDraft.move(stops, 0, -1))
        assertEquals(listOf(two), FlowerMapDraft.remove(stops, 0))
        assertEquals(stops, FlowerMapDraft.remove(stops, 5))
    }

    @Test fun zeroDistanceDoesNotCreateWalkOrFalseLoop() {
        assertThrows(IllegalArgumentException::class.java) {
            FlowerMapDraft.routePoints(origin, listOf(origin), true)
        }
        assertEquals(listOf(origin, one, two, origin),
            FlowerMapDraft.routePoints(origin, listOf(one, two), true))
    }
}
