package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import org.junit.Assert.*
import org.junit.Test

class WalkStopUiPolicyTest {
    @Test fun runningOrPausedRouteRequiresStopConfirmation() {
        assertTrue(WalkStopUiPolicy.canRequest(WalkMode.ROUTE, WalkPhase.MOVING))
        assertTrue(WalkStopUiPolicy.canRequest(WalkMode.ROUTE, WalkPhase.PAUSED))
    }

    @Test fun parkedIdleOrLegacyFlowerModeCannotSendStop() {
        assertFalse(WalkStopUiPolicy.canRequest(WalkMode.ROUTE, WalkPhase.PARKED))
        assertFalse(WalkStopUiPolicy.canRequest(WalkMode.ROUTE, WalkPhase.IDLE))
        assertFalse(WalkStopUiPolicy.canRequest(null, WalkPhase.MOVING))
        assertFalse(WalkStopUiPolicy.canRequest(WalkMode.FLOWERS, WalkPhase.MOVING))
    }
}
