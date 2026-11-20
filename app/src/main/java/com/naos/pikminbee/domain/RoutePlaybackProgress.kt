package com.naos.pikminbee.domain

import kotlin.math.roundToLong

/** Physical route-playback progress, not a claim that GPS has been accepted by a game. */
data class WalkingProgressSnapshot(
    val travelledMetres: Double = 0.0,
    val totalMetres: Double = 0.0,
    val nextWaypoint: Int? = null,
    val waypointCount: Int = 0,
    val remainingSeconds: Long = 0L,
    val looping: Boolean = false,
) {
    val fraction: Float get() = if (totalMetres <= 0.0) 0f
        else (travelledMetres / totalMetres).toFloat().coerceIn(0f, 1f)
}

object RoutePlaybackProgress {
    /** Match ordered user-picked stops to the closest forward point along the planned road. */
    fun thresholds(track: List<Coordinate>, stops: List<Coordinate>): List<Double> {
        if (track.size < 2 || stops.isEmpty()) return emptyList()
        val travelled = DoubleArray(track.size)
        for (i in 1 until track.size) {
            travelled[i] = travelled[i - 1] +
                WalkingGeometry.distanceMeters(track[i - 1], track[i])
        }
        var nextStart = 0
        return stops.map { stop ->
            var bestIndex = nextStart
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in nextStart until track.size) {
                val distance = WalkingGeometry.distanceMeters(track[i], stop)
                if (distance < bestDistance) {
                    bestIndex = i
                    bestDistance = distance
                }
            }
            nextStart = bestIndex
            travelled[bestIndex]
        }
    }

    fun snapshot(
        travelledMetres: Double,
        totalMetres: Double,
        speedMetresPerSecond: Double,
        stopThresholds: List<Double>,
        looping: Boolean,
    ): WalkingProgressSnapshot {
        val travelled = travelledMetres.coerceIn(0.0, totalMetres.coerceAtLeast(0.0))
        val reached = stopThresholds.count { travelled >= it - 0.1 }
        val nextStop = if (reached < stopThresholds.size) reached + 1 else null
        val eta = if (speedMetresPerSecond > 0.0)
            ((totalMetres - travelled) / speedMetresPerSecond).roundToLong().coerceAtLeast(0L)
        else 0L
        return WalkingProgressSnapshot(
            travelledMetres = travelled,
            totalMetres = totalMetres,
            nextWaypoint = nextStop,
            waypointCount = stopThresholds.size,
            remainingSeconds = eta,
            looping = looping,
        )
    }
}
