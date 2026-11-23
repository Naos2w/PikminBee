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

    @Test fun overlayDoesNotOfferRetiredFlowerVisionAction() {
        assertEquals(
            listOf(OverlayQuickAction.PRIMARY, OverlayQuickAction.STOP,
                OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS),
            OverlayQuickPolicy.topRow,
        )
        assertEquals(
            listOf(OverlayQuickAction.SPEED, OverlayQuickAction.MAP,
                OverlayQuickAction.CLOSE),
            OverlayQuickPolicy.bottomRow,
        )
        assertFalse(OverlayQuickPolicy.bottomRow.contains(OverlayQuickAction.FLOWER_VISION))
        assertEquals(7, (OverlayQuickPolicy.topRow + OverlayQuickPolicy.bottomRow).distinct().size)
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

    @Test fun gpsButtonsActuallySwitchOnlyWhenOtherModeSelected() {
        assertEquals(OverlayGpsPress.SWITCH_REAL, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.REAL_GPS, mockActive = true, hasRememberedVirtual = true))
        assertEquals(OverlayGpsPress.SWITCH_VIRTUAL, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.VIRTUAL_GPS, mockActive = false, hasRememberedVirtual = true))
        assertEquals(OverlayGpsPress.SELECT_VIRTUAL_POINT, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.VIRTUAL_GPS, mockActive = false, hasRememberedVirtual = false))
        // A click on the already-lit button must not stop or reset a running route.
        assertEquals(OverlayGpsPress.ALREADY_SELECTED, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.VIRTUAL_GPS, mockActive = true, hasRememberedVirtual = false))
        assertEquals(OverlayGpsPress.ALREADY_SELECTED, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.REAL_GPS, mockActive = false, hasRememberedVirtual = true))
        assertEquals(OverlayGpsPress.ALREADY_SELECTED, OverlayQuickPolicy.gpsPress(
            OverlayQuickAction.STOP, mockActive = true, hasRememberedVirtual = true))
    }

    @Test fun gpsModeHighlightRemainsProviderDrivenUntilSwitchSucceeds() {
        val request = OverlayQuickPolicy.gpsPress(OverlayQuickAction.VIRTUAL_GPS,
            mockActive = false, hasRememberedVirtual = true)
        assertEquals(OverlayGpsPress.SWITCH_VIRTUAL, request)
        assertEquals(OverlayQuickAction.REAL_GPS, OverlayQuickPolicy.selectedGps(false))
        assertEquals(OverlayQuickAction.VIRTUAL_GPS, OverlayQuickPolicy.selectedGps(true))
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
