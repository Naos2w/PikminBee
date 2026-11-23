package com.naos.pikminbee.domain

import com.naos.pikminbee.health.EstimatedStepCounter
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class MockGpsJitterTest {
    private val taipei = Coordinate(25.0375, 121.5637)

    @Test fun defaultIsOffAndInvalidStoredSettingIsOff() {
        assertEquals(MockGpsJitterLevel.OFF, MockGpsJitterLevel.DEFAULT)
        assertEquals(MockGpsJitterLevel.OFF, MockGpsJitterLevel.fromSaved(null))
        assertEquals(MockGpsJitterLevel.OFF, MockGpsJitterLevel.fromSaved("INVALID"))
        assertEquals(MockGpsJitterLevel.HIGH, MockGpsJitterLevel.fromSaved("HIGH"))
        val jitter = MockGpsJitter(Random(42))
        repeat(100) {
            assertEquals(taipei, jitter.report(taipei, MockGpsJitterLevel.OFF, true, true))
        }
    }

    @Test fun eachLevelIsBoundedAcrossLongRoute() {
        for (level in MockGpsJitterLevel.entries.filter { it != MockGpsJitterLevel.OFF }) {
            val jitter = MockGpsJitter(Random(4))
            repeat(1_000) { tick ->
                val planned = Coordinate(25.0375 + tick * 0.000001, 121.5637)
                val reported = jitter.report(planned, level, true, true)
                assertTrue(WalkingGeometry.distanceMeters(planned, reported) <= level.metres + 0.015)
            }
        }
    }

    @Test fun driftDoesNotJumpBetweenConsecutiveSamples() {
        val jitter = MockGpsJitter(Random(8))
        var lastNorth = 0.0
        var lastEast = 0.0
        repeat(350) {
            val fix = jitter.report(taipei, MockGpsJitterLevel.HIGH, true, true)
            val metresNorth = (fix.latitude - taipei.latitude) * 111_195.0
            val metresEast = (fix.longitude - taipei.longitude) * 100_750.0
            assertTrue(hypotenuse(metresNorth - lastNorth, metresEast - lastEast) < 0.40)
            lastNorth = metresNorth
            lastEast = metresEast
        }
    }

    @Test fun pauseAndParkDoNotAdvanceRandomOffset() {
        val jitter = MockGpsJitter(Random(12))
        val moving = jitter.report(taipei, MockGpsJitterLevel.HIGH, true, true)
        repeat(60) {
            assertEquals(moving, jitter.report(taipei, MockGpsJitterLevel.HIGH, true, false))
        }
    }

    @Test fun reducingRadiusWhilePausedNeverExceedsSmallerLimit() {
        val jitter = MockGpsJitter(Random(37))
        repeat(200) { jitter.report(taipei, MockGpsJitterLevel.HIGH, true, true) }
        val narrowed = jitter.report(taipei, MockGpsJitterLevel.LOW, true, false)
        assertTrue(WalkingGeometry.distanceMeters(taipei, narrowed) <= 0.51)
        repeat(100) {
            assertEquals(narrowed, jitter.report(taipei, MockGpsJitterLevel.LOW, true, false))
        }
    }

    @Test fun reseedMakesTestsReproducibleButRouteRestartResetsPosition() {
        val a = MockGpsJitter(Random(5))
        val b = MockGpsJitter(Random(5))
        repeat(50) {
            assertEquals(a.report(taipei, MockGpsJitterLevel.MEDIUM, true, true),
                b.report(taipei, MockGpsJitterLevel.MEDIUM, true, true))
        }
        a.reset()
        assertEquals(taipei, a.report(taipei, MockGpsJitterLevel.HIGH, true, false))
        val resumed = a.report(taipei, MockGpsJitterLevel.HIGH, true, true)
        assertNotEquals(taipei, resumed)
        assertEquals(taipei, a.report(taipei, MockGpsJitterLevel.HIGH, false, true))
    }

    @Test fun coordinatesStayValidNearDateLineAndPoles() {
        val jitter = MockGpsJitter(Random(42))
        for (origin in listOf(
            Coordinate(25.0, 179.999999), Coordinate(-25.0, -179.999999),
            Coordinate(89.99999, 120.0), Coordinate(-89.99999, -70.0),
            Coordinate(90.0, 180.0),
        )) {
            jitter.reset()
            repeat(200) {
                val reported = jitter.report(origin, MockGpsJitterLevel.HIGH, true, true)
                assertTrue(reported.latitude in -90.0..90.0)
                assertTrue(reported.longitude in -180.0..180.0)
            }
        }
    }

    @Test fun measuredWalkProgressAndEstimatedStepsIgnoreReportedJitter() {
        val route = listOf(taipei, Coordinate(25.0380, 121.5637))
        val tracker = EstimatedStepCounter()
        val baseline = EstimatedStepCounter()
        val jitter = MockGpsJitter(Random(21))
        var travelled = 0.0
        repeat(40) {
            travelled += 0.72
            tracker.addTravel(0.72) // the service consumes exact route distance, not jittered fixes
            baseline.addTravel(0.72)
            val trueFix = WalkingGeometry.sampleAt(route, travelled)
            jitter.report(trueFix, MockGpsJitterLevel.HIGH, true, true)
        }
        val parked = WalkingGeometry.sampleAt(route, travelled)
        repeat(120) { jitter.report(parked, MockGpsJitterLevel.HIGH, true, false) }
        // The existing all-time counter floors accumulated metres / stride, so
        // floating-point rounding can produce 39 rather than exactly 40.
        assertEquals(baseline.snapshot().estimatedSteps, tracker.snapshot().estimatedSteps)
        assertEquals(28.8, travelled, 1e-5)
    }

    private fun hypotenuse(a: Double, b: Double): Double = kotlin.math.hypot(a, b)
}
