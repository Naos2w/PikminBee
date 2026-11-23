package com.naos.pikminbee.domain

import kotlin.math.min

/**
 * One virtual route-playback tick. This is the only distance allowed to
 * increment estimated walking steps; reported GPS jitter is intentionally absent.
 *
 * Non-loop travel clamps to the actual route end. Existing looping routes may
 * cross multiple complete laps in one slow Android tick without losing distance.
 */
data class RouteTickMovement(
    val fromMetres: Double,
    val acceptedMetres: Double,
    val toMetres: Double,
    val arrived: Boolean,
)

object RouteTickProgression {
    fun advance(
        previousMetres: Double,
        routeLengthMetres: Double,
        speedMetresPerSecond: Double,
        elapsedSeconds: Double,
        looping: Boolean,
    ): RouteTickMovement {
        require(routeLengthMetres.isFinite() && routeLengthMetres >= 0.1)
        require(previousMetres.isFinite() && previousMetres >= 0.0)
        require(speedMetresPerSecond.isFinite() && speedMetresPerSecond >= 0.0)
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
        // Android handler delays are bounded like the real service tick.
        val elapsed = min(elapsedSeconds, 2.0)
        val old = if (looping) previousMetres % routeLengthMetres
            else previousMetres.coerceAtMost(routeLengthMetres)
        val requested = speedMetresPerSecond * elapsed
        val accepted = if (looping) requested
            else min(requested, (routeLengthMetres - old).coerceAtLeast(0.0))
        val next = if (looping) (old + accepted) % routeLengthMetres
            else (old + accepted).coerceAtMost(routeLengthMetres)
        val arrived = !looping && accepted > 0.0 &&
            next >= routeLengthMetres - 1e-6
        return RouteTickMovement(old, accepted, next, arrived)
    }
}
