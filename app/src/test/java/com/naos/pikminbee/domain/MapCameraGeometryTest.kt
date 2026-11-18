package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class MapCameraGeometryTest {
    private val start = Coordinate(25.0330, 121.5654)
    private val a = Coordinate(25.0360, 121.5672)
    private val b = Coordinate(25.0320, 121.5631)
    private val far = Coordinate(25.0380, 121.5712)

    @Test fun routeExtentIncludesStartAndEveryExtremeOfDraft() {
        val result = MapCameraGeometry.forRoute(start, listOf(a, b, far), emptyList())
        assertEquals(false, result.crossesDateLine)
        assertTrue(result.points.minOf { it.latitude } <= start.latitude)
        assertTrue(result.points.maxOf { it.latitude } >= start.latitude)
        assertTrue(result.points.minOf { it.longitude } <= start.longitude)
        assertTrue(result.points.maxOf { it.longitude } >= start.longitude)
        assertTrue(result.points.contains(b))
        assertTrue(result.points.contains(far))
        assertTrue(result.points.size <= 4)
    }

    @Test fun roadGeometryTakesPriorityOverDraftAndReducesTenThousandPoints() {
        val road = (0..9999).map {
            Coordinate(25.0330 + it * .0000002, 121.5654 + it * .0000003)
        }
        val result = MapCameraGeometry.forRoute(start, listOf(far), road)
        assertTrue(result.points.size <= 4)
        assertTrue(result.points.contains(road.last()))
        assertFalse(result.points.contains(far))
    }

    @Test fun noPointsAndOnePointProduceSafeCameraTargets() {
        assertFalse(MapCameraGeometry.fitPoints(emptyList()).hasAny)
        assertEquals(listOf(start), MapCameraGeometry.fitPoints(listOf(start)).points)
        assertEquals(listOf(start), MapCameraGeometry.fitPoints(listOf(start, start)).points)
        assertTrue(MapCameraGeometry.fitPoints(listOf(start)).isSingle)
    }

    @Test fun datelineCrossingUsesSafeSinglePointFallback() {
        val two = listOf(Coordinate(10.0, 179.96), Coordinate(10.0005, -179.97))
        val result = MapCameraGeometry.fitPoints(two)
        assertTrue(result.crossesDateLine)
        assertEquals(listOf(two.first()), result.points)
    }

    @Test fun overlappingFlowerMarkerSelectsExistingStopOnlyWithinThreshold() {
        val smallOffset = Coordinate(25.03601, 121.56721)
        assertEquals(0, MapCameraGeometry.overlappingStopIndex(smallOffset, listOf(a, b)))
        assertNull(MapCameraGeometry.overlappingStopIndex(far, listOf(a, b)))
        assertNull(MapCameraGeometry.overlappingStopIndex(far, emptyList()))
    }
}
