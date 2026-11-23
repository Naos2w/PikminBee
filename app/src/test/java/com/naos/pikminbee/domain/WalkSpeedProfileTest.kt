package com.naos.pikminbee.domain

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WalkSpeedProfileTest {
    @Test fun existingPresetsStayUnchanged() {
        assertEquals(0.8, WalkSpeedProfile.preset(WalkSpeedPreset.SLOW).estimatedKmh, 0.0)
        assertEquals(1.4, WalkSpeedProfile.preset(WalkSpeedPreset.WALK).sampleKmh(), 0.0)
        assertEquals(10.8, WalkSpeedProfile.preset(WalkSpeedPreset.FAST).sampleKmh(), 0.0)
    }
    @Test fun fixedRangeValidation() {
        assertEquals(15.0, WalkSpeedProfile.fixed(15.0).sampleKmh(), 0.0)
        assertNotNull(WalkSpeedProfile.tryFixed("12.5"))
        for (s in listOf("", "NaN", "Infinity", "20.1", "0.7"))
            assertNull(WalkSpeedProfile.tryFixed(s))
        assertNull(WalkSpeedProfile.tryRange("15", "12"))
        assertNull(WalkSpeedProfile.tryRange("12", "12"))
        assertNull(WalkSpeedProfile.tryRange("1.2", "100"))
        assertNull(WalkSpeedProfile.tryRange("", "9"))
        val seeded = Random(93)
        repeat(2_000) {
            val actual = WalkSpeedProfile.range(12.0, 15.0).sampleKmh(seeded)
            assertTrue(actual in 12.0..15.0)
        }
    }
    @Test fun onlyRouteLegBoundaryReRolls() {
        val playback = WalkSpeedPlayback(WalkSpeedProfile.range(12.0, 15.0), Random(1))
        val original = playback.appliedKmh
        playback.advance(0.0, 10.0, 100.0, listOf(30.0, 70.0, 100.0), false)
        assertEquals(original, playback.appliedKmh, 0.0)
        playback.advance(10.0, 25.0, 100.0, listOf(30.0, 70.0, 100.0), false)
        assertTrue(playback.appliedKmh in 12.0..15.0)
        assertNotEquals(original, playback.appliedKmh)
    }
    @Test fun pauseAndNoProgressPreserveSpeed() {
        val playback = WalkSpeedPlayback(WalkSpeedProfile.range(10.0, 15.0), Random(9))
        val original = playback.appliedKmh
        playback.advance(0.0, 0.0, 100.0, listOf(25.0), true)
        playback.advance(0.0, -1.0, 100.0, listOf(25.0), true)
        assertEquals(original, playback.appliedKmh, 0.0)
    }
    @Test fun loopRerollsAndCustomAppliesImmediately() {
        val playback = WalkSpeedPlayback(WalkSpeedProfile.range(12.0, 15.0), Random(9))
        val before = playback.appliedKmh
        playback.advance(95.0, 10.0, 100.0, listOf(40.0, 100.0), true)
        assertNotEquals(before, playback.appliedKmh)
        playback.apply(WalkSpeedProfile.fixed(6.3))
        assertEquals(6.3, playback.appliedKmh, 0.0)
        playback.advance(30.0, 50.0, 100.0, listOf(40.0, 100.0), true)
        assertEquals(6.3, playback.appliedKmh, 0.0)
    }
    @Test fun severalStopsInOneTickAreSafe() {
        val playback = WalkSpeedPlayback(WalkSpeedProfile.range(2.0, 3.0), Random(44))
        playback.advance(3.0, 20.0, 100.0, listOf(5.0, 10.0, 15.0), false)
        assertTrue(playback.appliedKmh in 2.0..3.0)
    }

    @Test fun selectingTheSameRangeDoesNotResampleUntilARealLegChange() {
        val range = WalkSpeedProfile.range(12.0, 15.0)
        val expected = Random(23)
        val first = range.sampleKmh(expected)
        val second = range.sampleKmh(expected)
        val playback = WalkSpeedPlayback(range, Random(23))
        assertEquals(first, playback.appliedKmh, 0.0)
        playback.apply(range)
        playback.apply(WalkSpeedProfile.range(12.0, 15.0))
        assertEquals(first, playback.appliedKmh, 0.0)
        playback.advance(1.0, 0.0, 100.0, listOf(20.0), true)
        assertEquals(first, playback.appliedKmh, 0.0)
        playback.advance(15.0, 10.0, 100.0, listOf(20.0), false)
        assertEquals(second, playback.appliedKmh, 0.0)
    }

    @Test fun restartingTheSameRangeSamplesANewInitialSpeed() {
        val range = WalkSpeedProfile.range(12.0, 15.0)
        val expected = Random(87)
        range.sampleKmh(expected)
        val next = range.sampleKmh(expected)
        val playback = WalkSpeedPlayback(range, Random(87))
        playback.restart(range)
        assertEquals(next, playback.appliedKmh, 0.0)
    }

    @Test fun fixedSpeedDoesNotResampleAcrossRepeatedRouteLaps() {
        val fixed = WalkSpeedProfile.fixed(10.8)
        val playback = WalkSpeedPlayback(fixed, Random(45))
        playback.advance(0.0, 23.0, 0.5, listOf(0.25), true)
        assertEquals(10.8, playback.appliedKmh, 0.0)
    }
}
