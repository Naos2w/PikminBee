package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGlassCaptureGateTest {
    @Test fun capturesDuringDragWithoutSnapshotPerFrame() {
        val gate = MapGlassCaptureGate()
        val first = gate.cameraStarted(0)
        assertNotNull(first)
        assertNull(gate.cameraMoved(16))
        assertNull(gate.cameraMoved(320)) // one outstanding callback at a time
        assertTrue(gate.complete(first!!, 80, true).accepted)
        assertNull(gate.cameraMoved(120))
        val second = gate.cameraMoved(321)
        assertNotNull(second)
        assertFalse(gate.complete(second!!, 390, true).accepted.not())
        assertNull(gate.cameraMoved(399))
        assertNotNull(gate.cameraMoved(650))
    }

    @Test fun finalIdleSnapshotReplacesOldMovingFrame() {
        val gate = MapGlassCaptureGate()
        val moving = gate.cameraStarted(0)!!
        assertNull(gate.cameraIdle(90))
        val result = gate.complete(moving, 120, true)
        assertFalse(result.accepted)
        assertNotNull(result.followUpId)
        assertTrue(gate.complete(result.followUpId!!, 150, true).accepted)
        assertNull(gate.cameraMoved(500))
    }

    @Test fun anotherGestureCancelsPendingIdleBeforeOldCaptureCompletes() {
        val gate = MapGlassCaptureGate()
        val first = gate.cameraStarted(0)!!
        assertNull(gate.cameraIdle(40))
        assertNull(gate.cameraStarted(80))
        assertTrue(gate.complete(first, 100, true).accepted)
        assertNotNull(gate.cameraMoved(330))
    }

    @Test fun timedOutSdkRequestIsSupersededAndLateCallbackIsIgnored() {
        val gate = MapGlassCaptureGate()
        val first = gate.cameraStarted(0)!!
        assertNull(gate.cameraMoved(1300))
        val replacement = gate.cameraMoved(1601)!!
        assertFalse(gate.complete(first, 1700, true).accepted)
        assertTrue(gate.complete(replacement, 1701, true).accepted)
    }

    @Test fun onlyStaleMovingImageFallsBackAndNewImageRestoresOptics() {
        val gate = MapGlassCaptureGate()
        val initial = gate.cameraStarted(0)!!
        assertTrue(gate.complete(initial, 100, true).accepted)
        assertFalse(gate.shouldClearStaleMovingBitmap(1150))
        assertTrue(gate.shouldClearStaleMovingBitmap(1200))
        assertFalse(gate.shouldClearStaleMovingBitmap(1500))
        val next = gate.cameraMoved(1501)!!
        assertTrue(gate.complete(next, 1530, true).accepted)
        assertFalse(gate.shouldClearStaleMovingBitmap(1540))
        gate.cameraIdle(1540)
        assertFalse(gate.shouldClearStaleMovingBitmap(4000))
    }

    @Test fun closeRejectsOutstandingAndFutureCaptures() {
        val gate = MapGlassCaptureGate()
        val first = gate.cameraStarted(0)!!
        gate.close()
        assertFalse(gate.complete(first, 50, true).accepted)
        assertNull(gate.cameraMoved(100))
        assertNull(gate.cameraIdle(100))
        assertNull(gate.cameraStarted(110))
    }

    @Test fun noBitmapOrNoCameraMovementNeverClaimsOpticalUpdates() {
        val gate = MapGlassCaptureGate()
        assertNull(gate.cameraMoved(0))
        val first = gate.cameraIdle(0)!!
        assertFalse(gate.complete(first, 10, false).accepted)
        assertFalse(gate.shouldClearStaleMovingBitmap(5000))
    }
}
