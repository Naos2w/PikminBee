package com.naos.pikminbee.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Controlled positional variation for the *reported test-provider fix only*.
 * Road geometry, walked distance, ETA, destination, and step counts stay exact.
 */
enum class MockGpsJitterLevel(val metres: Double) {
    OFF(0.0),
    LOW(0.5),
    MEDIUM(1.0),
    HIGH(1.5);

    companion object {
        val DEFAULT = OFF
        fun fromSaved(value: String?): MockGpsJitterLevel =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Bounded correlated drift. The offset evolves only on actual walking ticks;
 * while paused/parked it is held constant so stationary GPS does not wander.
 * Each route/teleport/provider lifecycle explicitly resets its local state.
 */
class MockGpsJitter(private val random: Random = Random.Default) {
    private var northMetres = 0.0
    private var eastMetres = 0.0

    fun reset() {
        northMetres = 0.0
        eastMetres = 0.0
    }

    fun report(
        planned: Coordinate,
        level: MockGpsJitterLevel,
        routeActive: Boolean,
        advanced: Boolean,
    ): Coordinate {
        if (!routeActive || level == MockGpsJitterLevel.OFF) {
            reset()
            return planned
        }
        if (advanced) {
            val maxStep = minOf(0.25, level.metres / 2.0)
            northMetres += random.nextDouble(-maxStep, maxStep)
            eastMetres += random.nextDouble(-maxStep, maxStep)
        }
        // A user can lower the radius while paused. The existing offset must
        // still respect the new bound without inventing another movement tick.
        val radius = hypot(northMetres, eastMetres)
        if (radius > level.metres) {
            val scale = level.metres / radius
            northMetres *= scale
            eastMetres *= scale
        }
        if (northMetres == 0.0 && eastMetres == 0.0) return planned

        // Longitude is undefined at the poles; never generate invalid fixes.
        if (kotlin.math.abs(planned.latitude) >= 89.9999) return planned
        val latitude = (planned.latitude + Math.toDegrees(northMetres / EARTH_RADIUS_METRES))
            .coerceIn(-90.0, 90.0)
        val longitudeDelta = Math.toDegrees(
            eastMetres / (EARTH_RADIUS_METRES * cos(planned.latitude * PI / 180.0))
        )
        val longitude = ((planned.longitude + longitudeDelta + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return Coordinate(latitude, longitude)
    }

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_000.0
    }
}
