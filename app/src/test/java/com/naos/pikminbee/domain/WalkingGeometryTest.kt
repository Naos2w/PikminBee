package com.naos.pikminbee.domain

import com.naos.pikminbee.route.ValhallaPlanner
import org.junit.Assert.*
import org.junit.Test

class WalkingGeometryTest {
    private val a = Coordinate(25.0330, 121.5654)
    private val b = Coordinate(25.0340, 121.5654)
    private val c = Coordinate(25.0350, 121.5654)

    @Test fun zeroLengthAndOneNodeStayInPlace() {
        assertEquals(0.0, WalkingGeometry.distanceMeters(a, a), 0.001)
        assertEquals(a, WalkingGeometry.sampleAt(listOf(a), 999.0))
    }

    @Test fun lengthSumsRoadSegments() {
        val total = WalkingGeometry.lengthMeters(listOf(a, b, c))
        assertEquals(WalkingGeometry.distanceMeters(a, b) +
            WalkingGeometry.distanceMeters(b, c), total, 0.001)
    }

    @Test fun partialTravelLiesBetweenFirstTwoPoints() {
        val halfway = WalkingGeometry.distanceMeters(a, b) * 0.5
        val result = WalkingGeometry.sampleAt(listOf(a, b, c), halfway)
        assertEquals(25.0335, result.latitude, 0.00001)
        assertEquals(a.longitude, result.longitude, 0.00001)
    }

    @Test fun stopDoesNotOvershootEnd() {
        val points = listOf(a, b, c)
        assertEquals(a, WalkingGeometry.sampleAt(points, -10.0))
        assertEquals(c, WalkingGeometry.sampleAt(points, 999_000.0))
        assertTrue(WalkingGeometry.hasArrived(points, WalkingGeometry.lengthMeters(points)))
    }

    @Test fun polyline6DecoderPreservesPrecision() {
        val points = listOf(a, b, c)
        assertEquals(points, ValhallaPlanner.decodePolyline6(encode(points)))
    }

    @Test fun corruptedPolylineFailsFast() {
        assertThrows(ValhallaPlanner.RouteException::class.java) {
            ValhallaPlanner.decodePolyline6("invalid~")
        }
    }

    private fun encode(points: List<Coordinate>): String {
        val result = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (point in points) {
            val lat = (point.latitude * 1_000_000).toLong()
            val lon = (point.longitude * 1_000_000).toLong()
            appendDelta(result, lat - lastLat)
            appendDelta(result, lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
        return result.toString()
    }

    private fun appendDelta(output: StringBuilder, delta: Long) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            output.append(((0x20 or (value and 0x1F).toInt()) + 63).toChar())
            value = value shr 5
        }
        output.append((value.toInt() + 63).toChar())
    }
}
