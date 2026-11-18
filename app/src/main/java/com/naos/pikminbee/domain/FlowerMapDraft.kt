package com.naos.pikminbee.domain

/** User-confirmed map selection; never assumes a map POI is a verified Big Flower. */
object FlowerMapDraft {
    const val MAX_STOPS = 14
    private const val NEARBY_METRES = 25.0

    enum class AddResult { ADDED, DUPLICATE, AT_START, FULL }

    data class Added(val stops: List<Coordinate>, val result: AddResult)

    fun add(stops: List<Coordinate>, origin: Coordinate, selected: Coordinate): Added {
        require(stops.size <= MAX_STOPS) { "既有花點超過允許數量" }
        return when {
            stops.size >= MAX_STOPS -> Added(stops, AddResult.FULL)
            WalkingGeometry.distanceMeters(origin, selected) < 2.0 ->
                Added(stops, AddResult.AT_START)
            stops.any { WalkingGeometry.distanceMeters(it, selected) <= NEARBY_METRES } ->
                Added(stops, AddResult.DUPLICATE)
            else -> Added(stops + selected, AddResult.ADDED)
        }
    }

    fun remove(stops: List<Coordinate>, index: Int): List<Coordinate> =
        if (index in stops.indices) stops.filterIndexed { i, _ -> i != index } else stops

    fun move(stops: List<Coordinate>, index: Int, direction: Int): List<Coordinate> {
        require(direction == -1 || direction == 1)
        val next = index + direction
        if (index !in stops.indices || next !in stops.indices) return stops
        return stops.toMutableList().apply {
            val old = this[index]
            this[index] = this[next]
            this[next] = old
        }
    }

    /** A start point is explicit, never silently taken from the first flower stop. */
    fun routePoints(origin: Coordinate, stops: List<Coordinate>, loop: Boolean): List<Coordinate> {
        require(stops.size <= MAX_STOPS) { "循環尋花最多 14 個花點" }
        val points = mutableListOf(origin)
        stops.forEach { stop ->
            if (WalkingGeometry.distanceMeters(points.last(), stop) > 1.0) points.add(stop)
        }
        if (loop && points.size >= 2 &&
            WalkingGeometry.distanceMeters(points.last(), origin) > 1.0) points.add(origin)
        require(points.size in 2..16) { "請先選取不同於起點的花點" }
        return points
    }
}
