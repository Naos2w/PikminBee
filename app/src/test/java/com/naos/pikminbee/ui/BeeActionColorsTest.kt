package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeeActionColorsTest {
    @Test fun startResumeAreGreenButMovingActionIsAmberPause() {
        assertEquals(BeeActionColors.START_GREEN, BeeActionColors.primary(WalkPhase.IDLE))
        assertEquals(BeeActionColors.START_GREEN, BeeActionColors.primary(WalkPhase.PAUSED))
        assertEquals(BeeActionColors.PAUSE_AMBER, BeeActionColors.primary(WalkPhase.MOVING))
        assertEquals("開始", BeeActionColors.primaryText(WalkPhase.IDLE))
        assertEquals("暫停", BeeActionColors.primaryText(WalkPhase.MOVING))
        assertEquals("繼續", BeeActionColors.primaryText(WalkPhase.PAUSED))
    }

    @Test fun enabledStopIsRedButIdleStopUsesNeutralSurface() {
        assertEquals(BeeActionColors.STOP_RED, BeeActionColors.stop(true))
        assertNull(BeeActionColors.stop(false))
    }

    @Test fun pausedLogoAndExpandedIdleLogoNeverLookTheSame() {
        assertNotEquals(BeeLogoPolicy.tint(false, true, WalkPhase.IDLE),
            BeeLogoPolicy.tint(false, true, WalkPhase.PAUSED))
        assertNotEquals(BeeLogoPolicy.tint(false, false, WalkPhase.IDLE),
            BeeLogoPolicy.tint(false, false, WalkPhase.PAUSED))
        assertNotEquals(BeeLogoPolicy.tint(false, true, WalkPhase.MOVING),
            BeeLogoPolicy.tint(false, true, WalkPhase.PAUSED))
    }
}
