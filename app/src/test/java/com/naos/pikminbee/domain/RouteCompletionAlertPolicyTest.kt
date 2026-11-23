package com.naos.pikminbee.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCompletionAlertPolicyTest {
    @Test fun nonLoopArrivalAlertsOnce() {
        assertTrue(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, looping = false, alreadyNotified = false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, looping = false, alreadyNotified = true))
    }

    @Test fun loopLapDoesNotAlert() {
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, looping = true, alreadyNotified = false))
    }

    @Test fun stopPauseResumeAndParkedTickAreNotArrival() {
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.IDLE, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PAUSED, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.PAUSED, WalkPhase.PARKED, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.PARKED, WalkPhase.PARKED, false, false))
    }
}
