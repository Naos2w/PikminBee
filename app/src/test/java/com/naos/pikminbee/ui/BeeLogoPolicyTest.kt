package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class BeeLogoPolicyTest {
    @Test fun inactiveBeeUsesHighContrastThemeInk() {
        assertEquals(BeeLogoPolicy.LIGHT_IDLE, BeeLogoPolicy.tint(false, false, WalkPhase.IDLE))
        assertEquals(BeeLogoPolicy.DARK_IDLE, BeeLogoPolicy.tint(true, false, WalkPhase.PARKED))
    }
    @Test fun expandedBeeUsesHoneyUnlessJourneyStatusOverrides() {
        assertEquals(BeeLogoPolicy.HONEY, BeeLogoPolicy.tint(false, true, WalkPhase.IDLE))
        assertEquals(BeeLogoPolicy.MOVING, BeeLogoPolicy.tint(true, true, WalkPhase.MOVING))
        assertEquals(BeeLogoPolicy.PAUSED, BeeLogoPolicy.tint(false, true, WalkPhase.PAUSED))
    }
    @Test fun motionOnlyFollowsActualWalkState() {
        assertEquals(BeeLogoPolicy.Motion.STILL, BeeLogoPolicy.motion(WalkPhase.PARKED))
        assertEquals(BeeLogoPolicy.Motion.FLYING, BeeLogoPolicy.motion(WalkPhase.MOVING))
        assertEquals(BeeLogoPolicy.Motion.HOVERING, BeeLogoPolicy.motion(WalkPhase.PAUSED))
    }
}
