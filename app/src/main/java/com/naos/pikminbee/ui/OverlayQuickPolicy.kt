package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset

/** First-level controls never edit routes or silently restart movement. */
enum class OverlayQuickAction {
    PRIMARY, STOP, REAL_GPS, VIRTUAL_GPS, FLOWER_VISION, SPEED, MAP, CLOSE
}

enum class OverlayPrimaryState { START, PAUSE, RESUME }

/** GPS mode toggles change only the OTHER mode; absence of a saved mock fix
 *  needs an explicit map point, never a fabricated/default coordinate. */
enum class OverlayGpsPress { ALREADY_SELECTED, SWITCH_REAL, SWITCH_VIRTUAL, SELECT_VIRTUAL_POINT }

object OverlayQuickPolicy {
    /** The retired flower-recognition shortcut is not shown; legacy data stays intact. */
    val topRow = listOf(
        OverlayQuickAction.PRIMARY, OverlayQuickAction.STOP,
        OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS,
    )
    val bottomRow = listOf(
        OverlayQuickAction.SPEED, OverlayQuickAction.MAP, OverlayQuickAction.CLOSE,
    )

    fun primary(phase: WalkPhase): OverlayPrimaryState = when (phase) {
        WalkPhase.MOVING -> OverlayPrimaryState.PAUSE
        WalkPhase.PAUSED -> OverlayPrimaryState.RESUME
        else -> OverlayPrimaryState.START
    }

    /** Exactly one GPS mode is highlighted from confirmed provider state, never the last tap. */
    fun selectedGps(mockActive: Boolean): OverlayQuickAction =
        if (mockActive) OverlayQuickAction.VIRTUAL_GPS else OverlayQuickAction.REAL_GPS

    /** Only the confirmed current GPS mode has an active visual indicator. */
    fun isGpsHighlighted(which: OverlayQuickAction, mockActive: Boolean): Boolean =
        which in listOf(OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS) &&
            which == selectedGps(mockActive)

    fun gpsPress(
        action: OverlayQuickAction, mockActive: Boolean, hasRememberedVirtual: Boolean,
    ): OverlayGpsPress = when {
        action !in listOf(OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS) ->
            OverlayGpsPress.ALREADY_SELECTED
        action == selectedGps(mockActive) -> OverlayGpsPress.ALREADY_SELECTED
        action == OverlayQuickAction.REAL_GPS -> OverlayGpsPress.SWITCH_REAL
        hasRememberedVirtual -> OverlayGpsPress.SWITCH_VIRTUAL
        else -> OverlayGpsPress.SELECT_VIRTUAL_POINT
    }

    fun nextSpeed(current: WalkSpeedPreset): WalkSpeedPreset {
        val entries = WalkSpeedPreset.entries
        return entries[(entries.indexOf(current) + 1) % entries.size]
    }

    fun canStop(phase: WalkPhase, planning: Boolean): Boolean =
        phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED || planning

    fun virtualRestoreNeverResumesWalking(): WalkPhase = WalkPhase.PARKED
}
