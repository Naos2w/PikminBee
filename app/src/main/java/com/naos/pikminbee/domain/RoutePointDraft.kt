package com.naos.pikminbee.domain

/**
 * The map is the route editor: each deliberate map tap creates numbered waypoint
 * 1, 2, 3 ... immediately, or replaces a specifically selected numbered marker.
 * A destination is never silently treated as the walk origin.
 */
object RoutePointDraft {
    const val MAX_STOPS = 15
    private const val NEARBY_METRES = 2.0

    enum class Outcome { ADDED, REPLACED, TOO_CLOSE_TO_ORIGIN, DUPLICATE, FULL, INVALID_INDEX }

    data class Update(val stops: List<Coordinate>, val outcome: Outcome, val index: Int = -1)

    fun select(
        stops: List<Coordinate>, origin: Coordinate?,
        picked: Coordinate, replaceIndex: Int? = null,
    ): Update {
        if (replaceIndex != null && replaceIndex !in stops.indices)
            return Update(stops, Outcome.INVALID_INDEX)
        if (origin != null && WalkingGeometry.distanceMeters(origin, picked) < NEARBY_METRES)
            return Update(stops, Outcome.TOO_CLOSE_TO_ORIGIN)
        if (stops.indices.any { it != replaceIndex &&
                WalkingGeometry.distanceMeters(stops[it], picked) < NEARBY_METRES })
            return Update(stops, Outcome.DUPLICATE)
        if (replaceIndex != null) return Update(
            stops.mapIndexed { index, old -> if (index == replaceIndex) picked else old },
            Outcome.REPLACED, replaceIndex,
        )
        if (stops.size >= MAX_STOPS) return Update(stops, Outcome.FULL)
        return Update(stops + picked, Outcome.ADDED, stops.size)
    }

    fun undo(stops: List<Coordinate>): List<Coordinate> =
        if (stops.isEmpty()) stops else stops.dropLast(1)

    fun remove(stops: List<Coordinate>, index: Int): List<Coordinate> =
        if (index in stops.indices) stops.filterIndexed { i, _ -> i != index } else stops
}
