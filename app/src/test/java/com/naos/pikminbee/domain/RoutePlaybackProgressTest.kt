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

    @Test fun emptyRoadDoesNotInventProgress() {
        assertTrue(RoutePlaybackProgress.thresholds(emptyList(), emptyList()).isEmpty())
        val value = RoutePlaybackProgress.snapshot(50.0, 0.0, 0.0, emptyList(), false)
        assertEquals(0f, value.fraction, 0f)
        assertNull(value.nextWaypoint)
    }
}
