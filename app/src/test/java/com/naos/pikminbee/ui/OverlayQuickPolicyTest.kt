package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayQuickPolicyTest {
    @Test fun primaryReflectsJourneyState() {
        assertEquals(OverlayPrimaryState.START, OverlayQuickPolicy.primary(WalkPhase.IDLE))
        assertEquals(OverlayPrimaryState.PAUSE, OverlayQuickPolicy.primary(WalkPhase.MOVING))
        assertEquals(OverlayPrimaryState.RESUME, OverlayQuickPolicy.primary(WalkPhase.PAUSED))
        assertEquals(OverlayPrimaryState.START, OverlayQuickPolicy.primary(WalkPhase.PARKED))
    }

    @Test fun speedCyclesWithoutChangingSavedDefaults() {
        assertEquals(WalkSpeedPreset.WALK, OverlayQuickPolicy.nextSpeed(WalkSpeedPreset.SLOW))
        assertEquals(WalkSpeedPreset.FAST, OverlayQuickPolicy.nextSpeed(WalkSpeedPreset.WALK))
        assertEquals(WalkSpeedPreset.SLOW, OverlayQuickPolicy.nextSpeed(WalkSpeedPreset.FAST))
    }

    @Test fun stopDoesNotStandInForCloseAndVirtualRestoreStaysParked() {
        assertFalse(OverlayQuickPolicy.canStop(WalkPhase.IDLE, false))
        assertTrue(OverlayQuickPolicy.canStop(WalkPhase.IDLE, true))
        assertTrue(OverlayQuickPolicy.canStop(WalkPhase.MOVING, false))
        assertEquals(WalkPhase.PARKED, OverlayQuickPolicy.virtualRestoreNeverResumesWalking())
    }
}
