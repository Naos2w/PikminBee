package com.naos.pikminbee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlaybackProgressTest {
    @Test fun orderedRoadThresholdsFollowForwardPolyline() {
        val a = Coordinate(25.0330, 121.5654)
        val b = Coordinate(25.0340, 121.5664)
        val c = Coordinate(25.0350, 121.5674)
        val track = listOf(a,b,c)
        val values = RoutePlaybackProgress.thresholds(track, listOf(b,c))
        assertEquals(2, values.size)
        assertTrue(values[0] > 0.0)
        assertTrue(values[1] > values[0])
    }

    @Test fun snapshotHasCorrectProgressAndEstimatedRemainingTime() {
        val progress = RoutePlaybackProgress.snapshot(
            250.0, 1000.0, 2.0, listOf(300.0, 1000.0), false)
        assertEquals(0.25f, progress.fraction, 0.0001f)
        assertEquals(1, progress.nextWaypoint)
        assertEquals(2, progress.waypointCount)
        assertEquals(375L, progress.remainingSeconds)
        val complete = RoutePlaybackProgress.snapshot(
            1001.0, 1000.0, 2.0, listOf(300.0, 1000.0), false)
        assertNull(complete.nextWaypoint)
        assertEquals(1f, complete.fraction, 0f)
        assertEquals(0L, complete.remainingSeconds)
    }

    @Test fun betweenSecondAndThirdWaypointShowsActualLeg() {
        val checkpoints = listOf(350.0, 900.0, 1400.0, 2000.0)
        val progress = RoutePlaybackProgress.snapshot(
            1150.0, 2000.0, 1.4, checkpoints, false)
        assertEquals(3, progress.nextWaypoint)
        assertEquals("2 → 3", progress.activeLegLabel)
        assertEquals(4, progress.waypointCount)
    }

    @Test fun startAndArrivalHaveExplicitLegLabels() {
        val checkpoints = listOf(350.0, 900.0, 1400.0, 2000.0)
        assertEquals("起點 → 1", RoutePlaybackProgress.snapshot(
            100.0, 2000.0, 1.4, checkpoints, false).activeLegLabel)
        assertEquals("已抵達第 4 點", RoutePlaybackProgress.snapshot(
            2000.0, 2000.0, 1.4, checkpoints, false).activeLegLabel)
    }

    @Test fun emptyRoadDoesNotInventProgress() {
        assertTrue(RoutePlaybackProgress.thresholds(emptyList(), emptyList()).isEmpty())
        val value = RoutePlaybackProgress.snapshot(50.0, 0.0, 0.0, emptyList(), false)
        assertEquals(0f, value.fraction, 0f)
        assertNull(value.nextWaypoint)
    }
}
