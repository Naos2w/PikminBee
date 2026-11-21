package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset

/** First-level controls never edit routes or silently restart movement. */
enum class OverlayQuickAction {
    PRIMARY, STOP, REAL_GPS, VIRTUAL_GPS, FLOWER_VISION, SPEED, MAP, CLOSE
}

enum class OverlayPrimaryState { START, PAUSE, RESUME }

object OverlayQuickPolicy {
    /** Keep the previous UAT's compact 2×4 overlay controls. */
    val topRow = listOf(
        OverlayQuickAction.PRIMARY, OverlayQuickAction.STOP,
        OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS,
    )
    val bottomRow = listOf(
        OverlayQuickAction.FLOWER_VISION, OverlayQuickAction.SPEED,
        OverlayQuickAction.MAP, OverlayQuickAction.CLOSE,
    )

    fun primary(phase: WalkPhase): OverlayPrimaryState = when (phase) {
        WalkPhase.MOVING -> OverlayPrimaryState.PAUSE
        WalkPhase.PAUSED -> OverlayPrimaryState.RESUME
        else -> OverlayPrimaryState.START
    }

    fun nextSpeed(current: WalkSpeedPreset): WalkSpeedPreset {
        val entries = WalkSpeedPreset.entries
        return entries[(entries.indexOf(current) + 1) % entries.size]
    }

    fun canStop(phase: WalkPhase, planning: Boolean): Boolean =
        phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED || planning

    fun virtualRestoreNeverResumesWalking(): WalkPhase = WalkPhase.PARKED
}
