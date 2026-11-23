package com.naos.pikminbee.domain

import com.naos.pikminbee.health.EstimatedStepCounter
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class RouteTickProgressionTest {
    @Test fun presetSpeedAndElapsedTimeProduceExpectedCanonicalDistanceAndSteps() {
        for (preset in WalkSpeedPreset.entries) {
            val steps = EstimatedStepCounter()
            var distance = 0.0
            repeat(60) {
                val tick = RouteTickProgression.advance(
                    distance, 1_000.0, preset.metresPerSecond, 1.0, false)
                distance = tick.toMetres
                steps.addTravel(tick.acceptedMetres)
            }
            assertEquals(preset.metresPerSecond * 60.0, distance, 1e-8)
            assertEquals(distance, steps.snapshot().totalMetres, 1e-8)
            assertTrue(steps.snapshot().estimatedSteps > 0L)
        }
    }

    @Test fun nonLoopArrivalClampsDistanceInsteadOfOvershootingEnd() {
        val tick = RouteTickProgression.advance(99.0, 100.0, 5.0, 1.0, false)
        assertEquals(99.0, tick.fromMetres, 0.0)
        assertEquals(1.0, tick.acceptedMetres, 0.0)
        assertEquals(100.0, tick.toMetres, 0.0)
        assertTrue(tick.arrived)
        val steps = EstimatedStepCounter()
        steps.addTravel(tick.acceptedMetres)
        assertEquals(1.0, steps.snapshot().totalMetres, 0.0)
        assertEquals(0.0, RouteTickProgression.advance(
            tick.toMetres, 100.0, 5.0, 1.0, false).acceptedMetres, 0.0)
    }

    @Test fun zeroOrPausedTicksDoNotGenerateDistanceOrSteps() {
        val steps = EstimatedStepCounter()
        var distance = 12.0
        for (dt in listOf(0.0, 0.0, 0.0)) {
            val tick = RouteTickProgression.advance(
                distance, 100.0, 3.0, dt, false)
            distance = tick.toMetres
            steps.addTravel(tick.acceptedMetres)
        }
        assertEquals(12.0, distance, 0.0)
        assertEquals(0L, steps.snapshot().estimatedSteps)
    }

    @Test fun delayedHandlerTickCannotChargeEntireBackgroundPause() {
        val tick = RouteTickProgression.advance(10.0, 100.0, 3.0, 20.0, false)
        assertEquals(6.0, tick.acceptedMetres, 0.0)
        assertEquals(16.0, tick.toMetres, 0.0)
    }

    @Test fun completedLoopsMustCountAllDistanceEvenIfFinalPointIsIdentical() {
        val tick = RouteTickProgression.advance(0.0, 0.5, 2.5, 1.2, true)
        assertEquals(3.0, tick.acceptedMetres, 1e-12)
        assertEquals(0.0, tick.toMetres, 1e-12)
        assertFalse(tick.arrived)

        val counter = EstimatedStepCounter()
        counter.addTravel(tick.acceptedMetres)
        assertEquals(tick.acceptedMetres, counter.snapshot().totalMetres, 1e-12)

        // 6 full laps: sample at the .25 m stop and lap end every lap,
        // not once based on the final, modulo-only coordinate.
        val range = WalkSpeedProfile.range(10.0, 15.0)
        val expectedRandom = Random(42)
        val expectedInitial = range.sampleKmh(expectedRandom)
        var expectedFinal = expectedInitial
        repeat(12) { expectedFinal = range.sampleKmh(expectedRandom) }
        val playback = WalkSpeedPlayback(range, Random(42))
        playback.advance(tick.fromMetres, tick.acceptedMetres, 0.5,
            listOf(0.25, 0.5), looping = true)
        assertEquals(expectedFinal, playback.appliedKmh, 0.0)
    }

    @Test fun rangeSpeedHotSwapWhileMovingDoesNotResetRouteOrVirtualStepCounter() {
        val playback = WalkSpeedPlayback(WalkSpeedProfile.preset(WalkSpeedPreset.WALK), Random(12))
        val tracker = EstimatedStepCounter()
        val first = RouteTickProgression.advance(0.0, 200.0,
            playback.appliedKmh / 3.6, 1.0, false)
        tracker.addTravel(first.acceptedMetres)
        val before = tracker.snapshot().estimatedSteps

        playback.apply(WalkSpeedProfile.fixed(15.0))
        assertEquals(15.0, playback.appliedKmh, 0.0)
        val paused = RouteTickProgression.advance(first.toMetres,
            200.0, playback.appliedKmh / 3.6, 0.0, false)
        assertEquals(0.0, paused.acceptedMetres, 0.0)
        assertEquals(before, tracker.snapshot().estimatedSteps)

        val resumed = RouteTickProgression.advance(paused.toMetres,
            200.0, playback.appliedKmh / 3.6, 1.0, false)
        tracker.addTravel(resumed.acceptedMetres)
        assertTrue(resumed.toMetres > first.toMetres)
        assertEquals(first.toMetres + 15.0 / 3.6, resumed.toMetres, 1e-9)
        assertEquals(first.acceptedMetres + resumed.acceptedMetres,
            tracker.snapshot().totalMetres, 1e-9)
    }

    @Test fun gpsJitterCannotFabricateRouteDistanceOrEstimatedSteps() {
        val geometry = listOf(Coordinate(25.03, 121.56), Coordinate(25.03, 121.561))
        val length = WalkingGeometry.lengthMeters(geometry)
        val jitter = MockGpsJitter(Random(13))
        val withJitter = EstimatedStepCounter()
        val withoutJitter = EstimatedStepCounter()
        var distance = 0.0
        repeat(40) {
            val tick = RouteTickProgression.advance(distance, length, 3.0, 1.0, false)
            distance = tick.toMetres
            val planned = WalkingGeometry.sampleAt(geometry, distance)
            jitter.report(planned, MockGpsJitterLevel.HIGH,
                routeActive = true, advanced = tick.acceptedMetres > 0.0)
            withJitter.addTravel(tick.acceptedMetres)
            withoutJitter.addTravel(tick.acceptedMetres)
        }
        // Even after parking with a reported offset, the step ledger does not change.
        jitter.report(WalkingGeometry.sampleAt(geometry, distance),
            MockGpsJitterLevel.HIGH, routeActive = true, advanced = false)
        assertEquals(withoutJitter.snapshot(), withJitter.snapshot())
        assertEquals(length, withJitter.snapshot().totalMetres, 1e-8)
    }

    @Test fun invalidInputCannotAdvanceTheEngine() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteTickProgression.advance(1.0, 0.0, 1.0, 1.0, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteTickProgression.advance(Double.NaN, 100.0, 1.0, 1.0, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteTickProgression.advance(1.0, 100.0, Double.NaN, 1.0, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteTickProgression.advance(1.0, 100.0, 1.0, -1.0, false)
        }
    }
}
