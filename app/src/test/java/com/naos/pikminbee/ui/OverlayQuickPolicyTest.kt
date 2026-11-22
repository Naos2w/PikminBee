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

    @Test fun originalFloatingPanelKeepsItsOwnTwoRows() {
        assertEquals(
            listOf(OverlayQuickAction.PRIMARY, OverlayQuickAction.STOP,
                OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS),
            OverlayQuickPolicy.topRow,
        )
        assertEquals(
            listOf(OverlayQuickAction.FLOWER_VISION, OverlayQuickAction.SPEED,
                OverlayQuickAction.MAP, OverlayQuickAction.CLOSE),
            OverlayQuickPolicy.bottomRow,
        )
        assertEquals(8, (OverlayQuickPolicy.topRow + OverlayQuickPolicy.bottomRow).distinct().size)
    }

    @Test fun gpsModeIndicatorReflectsActualMockProviderNotLastClick() {
        assertEquals(OverlayQuickAction.REAL_GPS, OverlayQuickPolicy.selectedGps(false))
        assertEquals(OverlayQuickAction.VIRTUAL_GPS, OverlayQuickPolicy.selectedGps(true))
    }

    @Test fun onlyOneGpsButtonCanBeHighlightedAndOtherActionsNeverLightUp() {
        val actions = OverlayQuickPolicy.topRow + OverlayQuickPolicy.bottomRow
        for (mockActive in listOf(false, true)) {
            val active = actions.filter { OverlayQuickPolicy.isGpsHighlighted(it, mockActive) }
            assertEquals(listOf(OverlayQuickPolicy.selectedGps(mockActive)), active)
            assertFalse(OverlayQuickPolicy.isGpsHighlighted(OverlayQuickAction.MAP, mockActive))
        }
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
