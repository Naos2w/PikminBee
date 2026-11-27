package com.naos.pikminbee.domain

/**
 * In-process preview. The exact routed geometry is used when playback starts.
 * A moved waypoint, changed loop setting, or materially moved origin invalidates
 * it. Small GPS jitter does not.
 */
data class PreparedWalkingRoute(
    val origin: Coordinate,
    val stops: List<Coordinate>,
    val geometry: List<Coordinate>,
    val loop: Boolean = false,
    val snapMetres: List<Double> = emptyList(),
) {
    init {
        require(geometry.size >= 2)
        require(snapMetres.all { it.isFinite() && it >= 0.0 })
    }

    val maxSnapMetres: Double get() = snapMetres.maxOrNull() ?: 0.0
    val originSnapMetres: Double get() = snapMetres.firstOrNull() ?: 0.0
    val requiresSnapConfirmation: Boolean get() = maxSnapMetres > 40.0

    /** The drawing belongs to the current draft even if the live start later moves. */
    fun matchesDraft(currentStops: List<Coordinate>, currentLoop: Boolean): Boolean =
        stops == currentStops && loop == currentLoop

    fun isCurrent(
        currentOrigin: Coordinate?,
        currentStops: List<Coordinate>,
        currentLoop: Boolean = loop,
    ): Boolean =
        currentOrigin != null && matchesDraft(currentStops, currentLoop) &&
            WalkingGeometry.distanceMeters(origin, currentOrigin) <= 30.0
}
