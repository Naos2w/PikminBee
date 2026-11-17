package com.naos.pikminbee.domain

import kotlin.math.*

/** Shared WGS84 geometry for map drawing, route playback and testable movement. */
object WalkingGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = sin(lat / 2)
        val sinLon = sin(lon / 2)
        val h = (sinLat * sinLat + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sinLon * sinLon).coerceIn(0.0, 1.0)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun lengthMeters(points: List<Coordinate>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

    fun sampleAt(points: List<Coordinate>, travelledMeters: Double): Coordinate {
        require(points.isNotEmpty()) { "A route requires at least one point" }
        var remaining = travelledMeters.coerceAtLeast(0.0)
        points.zipWithNext().forEach { (start, end) ->
            val segment = distanceMeters(start, end)
            if (segment < 0.01) return@forEach
            if (remaining <= segment) {
                val fraction = remaining / segment
                return Coordinate(
                    start.latitude + (end.latitude - start.latitude) * fraction,
                    start.longitude + (end.longitude - start.longitude) * fraction,
                )
            }
            remaining -= segment
        }
        return points.last()
    }

    fun hasArrived(points: List<Coordinate>, travelledMeters: Double): Boolean =
        points.isNotEmpty() && travelledMeters >= lengthMeters(points) - 0.1
}

/** Local workflow deliberately keeps "stop walking" different from "restore real GPS". */
enum class WalkPhase { IDLE, MOVING, PAUSED, PARKED }
enum class WalkMode { ROUTE, FLOWERS }
