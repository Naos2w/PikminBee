package com.naos.pikminbee.domain

/**
 * Camera-only route extent. Use road geometry if available, otherwise the draft.
 * Compressing to geographic extrema avoids shipping thousands of polyline nodes
 * to map SDK camera builders on the UI thread.
 */
object MapCameraGeometry {
    data class ViewTarget(
        val points: List<Coordinate>,
        val crossesDateLine: Boolean = false,
    ) {
        val hasAny: Boolean get() = points.isNotEmpty()
        val isSingle: Boolean get() = points.size == 1
    }

    fun forRoute(
        start: Coordinate?,
        stops: List<Coordinate>,
        road: List<Coordinate>,
    ): ViewTarget {
        val source = if (road.size >= 2) road else stops
        val points = buildList {
            if (start != null) add(start)
            addAll(source)
        }
        return fitPoints(points)
    }

    fun fitPoints(points: List<Coordinate>): ViewTarget {
        if (points.isEmpty()) return ViewTarget(emptyList())
        if (points.size == 1) return ViewTarget(points)
        val west = points.minBy { it.longitude }
        val east = points.maxBy { it.longitude }
        val north = points.maxBy { it.latitude }
        val south = points.minBy { it.latitude }
        // A single west/east bounds rectangle cannot safely represent a route
        // crossing the antimeridian with both native providers. Avoid globe zoom.
        val crosses = east.longitude - west.longitude > 180.0
        if (crosses) return ViewTarget(listOf(points.first()), crossesDateLine = true)
        return ViewTarget(listOf(north, south, west, east).distinct())
    }

    /** When a flower database marker obscures a route pin, prefer editing that pin. */
    fun overlappingStopIndex(
        flower: Coordinate,
        stops: List<Coordinate>,
        thresholdMetres: Double = 8.0,
    ): Int? {
        require(thresholdMetres in 0.0..50.0)
        val index = stops.indexOfFirst {
            WalkingGeometry.distanceMeters(it, flower) <= thresholdMetres
        }
        return index.takeIf { it >= 0 }
    }
}
