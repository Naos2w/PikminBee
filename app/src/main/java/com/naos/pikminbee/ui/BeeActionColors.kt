package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase

/**
 * PikminBee-only action/state semantics shared by the main map and cross-app overlay.
 * Colours are intentionally vivid for controls, but not neon or global theme options.
 */
internal object BeeActionColors {
    const val START_GREEN = 0xFF23A66F.toInt()
    const val STOP_RED = 0xFFD94A52.toInt()
    const val PAUSE_AMBER = 0xFFF0B64F.toInt()
    const val PAUSED_BLUE = 0xFF56A0D2.toInt()
    const val HONEY_EXPANDED = 0xFFD9AC4E.toInt()
    const val ON_BRIGHT = 0xFF172126.toInt()
    const val ON_DANGER = 0xFFFFFFFF.toInt()

    fun primary(phase: WalkPhase): Int =
        if (phase == WalkPhase.MOVING) PAUSE_AMBER else START_GREEN

    fun primaryText(phase: WalkPhase): String = when (phase) {
        WalkPhase.MOVING -> "暫停"
        WalkPhase.PAUSED -> "繼續"
        else -> "開始"
    }

    fun stop(available: Boolean): Int? = if (available) STOP_RED else null
}
