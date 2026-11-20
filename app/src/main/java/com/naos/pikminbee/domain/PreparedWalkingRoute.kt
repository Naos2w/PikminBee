package com.naos.pikminbee.domain

/**
 * A road preview belongs to exactly one set of route stops and a nearby origin.
 * Small GPS jitter does not discard it, but a moved start or edited stop does.
 * The displayed preview geometry is also the geometry passed to playback.
 */
data class PreparedWalkingRoute(
    val origin: Coordinate,
    val stops: List<Coordinate>,
    val geometry: List<Coordinate>,
) {
    init { require(geometry.size >= 2) }

    fun isCurrent(currentOrigin: Coordinate?, currentStops: List<Coordinate>): Boolean =
        currentOrigin != null &&
            stops == currentStops &&
            WalkingGeometry.distanceMeters(origin, currentOrigin) <= 30.0
}
