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

    @Test fun replacesOnlySelectedStopAndKeepsRouteOrder() {
        val newPlace = Coordinate(25.0348, 121.5680)
        val replaced = FlowerMapDraft.replace(listOf(one, two), origin, 0, newPlace)
        assertEquals(FlowerMapDraft.ReplaceResult.REPLACED, replaced.result)
        assertEquals(listOf(newPlace, two), replaced.stops)
        assertEquals(listOf(origin, newPlace, two), FlowerMapDraft.routePoints(
            origin, replaced.stops, false,
        ))
    }

    @Test fun rejectsDuplicateOrStartWhenEditingWithoutMutatingDraft() {
        val original = listOf(one, two)
        val duplicate = FlowerMapDraft.replace(original, origin, 0, two)
        assertEquals(FlowerMapDraft.ReplaceResult.DUPLICATE, duplicate.result)
        assertEquals(original, duplicate.stops)
        val atStart = FlowerMapDraft.replace(original, origin, 1, origin)
        assertEquals(FlowerMapDraft.ReplaceResult.AT_START, atStart.result)
        assertEquals(original, atStart.stops)
    }

    @Test fun editingOldPointOrOutOfRangeIsNonDestructive() {
        val original = listOf(one, two)
        assertEquals(FlowerMapDraft.ReplaceResult.UNCHANGED,
            FlowerMapDraft.replace(original, origin, 0, one).result)
        val invalid = FlowerMapDraft.replace(original, origin, 4, Coordinate(25.036, 121.567))
        assertEquals(FlowerMapDraft.ReplaceResult.OUT_OF_RANGE, invalid.result)
        assertEquals(original, invalid.stops)
    }

    @Test fun movingExistingStopWithinDuplicateRadiusIsAllowed() {
        val original = listOf(one, two)
        val moved = Coordinate(25.03351, 121.56542)
        val result = FlowerMapDraft.replace(original, origin, 0, moved)
        assertEquals(FlowerMapDraft.ReplaceResult.REPLACED, result.result)
        assertEquals(listOf(moved, two), result.stops)
    }

    @Test fun replacementAtMaximumCapacityDoesNotAppendStops() {
        val stops = (1..14).map { i -> Coordinate(25.034 + i * .001, 121.5654) }
        val replacement = Coordinate(25.0342, 121.5670)
        val result = FlowerMapDraft.replace(stops, origin, 13, replacement)
        assertEquals(FlowerMapDraft.ReplaceResult.REPLACED, result.result)
        assertEquals(14, result.stops.size)
        assertEquals(stops.take(13) + replacement, result.stops)
    }

    @Test fun zeroDistanceDoesNotCreateWalkOrFalseLoop() {
        assertThrows(IllegalArgumentException::class.java) {
            FlowerMapDraft.routePoints(origin, listOf(origin), true)
        }
        assertEquals(listOf(origin, one, two, origin),
            FlowerMapDraft.routePoints(origin, listOf(one, two), true))
    }
}
