package com.naos.pikminbee.domain

/**
 * Visual-only segmentation of the already displayed route.
 * Uses the same ordered-waypoint boundaries as the existing progress snapshot.
 * Never mutates route playback, position, or stops.
 */
data class RouteMapSegments(
    val completed: List<Coordinate> = emptyList(),
    val active: List<Coordinate> = emptyList(),
) {
    companion object {
        fun from(road: List<Coordinate>, progress: WalkingProgressSnapshot): RouteMapSegments {
            val from = progress.activeLegStartMetres ?: return RouteMapSegments()
            val to = progress.activeLegEndMetres ?: return RouteMapSegments()
            if (road.size < 2 || !from.isFinite() || !to.isFinite() || from < 0.0 || to <= from)
                return RouteMapSegments()
            var distance = 0.0
            var startIndex = 0
            var endIndex = 0
            var startError = kotlin.math.abs(from)
            var endError = kotlin.math.abs(to)
            for (i in 1 until road.size) {
                distance += WalkingGeometry.distanceMeters(road[i - 1], road[i])
                val a = kotlin.math.abs(distance - from)
                val b = kotlin.math.abs(distance - to)
                if (a < startError) { startIndex = i; startError = a }
                if (b < endError) { endIndex = i; endError = b }
            }
            // Geometry and progress may arrive in separate updates. Do not
            // highlight a guessed segment if the boundaries do not match.
            if (!progress.totalMetres.isFinite() || kotlin.math.abs(distance - progress.totalMetres) > 0.5 ||
                startError > 0.5 || endError > 0.5 || endIndex <= startIndex)
                return RouteMapSegments()
            return RouteMapSegments(
                completed = if (startIndex > 0) road.subList(0, startIndex + 1).toList() else emptyList(),
                active = road.subList(startIndex, endIndex + 1).toList(),
            )
        }
    }
}
