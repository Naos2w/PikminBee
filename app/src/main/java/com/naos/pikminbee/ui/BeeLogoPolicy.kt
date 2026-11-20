package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase

/** Product-specific honey/green accents over a fixed Mineral + Regular palette. */
internal object BeeLogoPolicy {
    const val HONEY = BeeActionColors.HONEY_EXPANDED
    const val MOVING = BeeActionColors.START_GREEN
    // Paused = distinctly cool blue, not the yellow reserved for expansion.
    const val PAUSED = BeeActionColors.PAUSED_BLUE
    const val DARK_IDLE = 0xFFF5F7F7.toInt()
    const val LIGHT_IDLE = 0xFF202B30.toInt()

    enum class Motion { STILL, FLYING, HOVERING }

    fun tint(dark: Boolean, expanded: Boolean, phase: WalkPhase): Int = when (phase) {
        WalkPhase.MOVING -> MOVING
        WalkPhase.PAUSED -> PAUSED
        else -> if (expanded) HONEY else if (dark) DARK_IDLE else LIGHT_IDLE
    }

    fun motion(phase: WalkPhase): Motion = when (phase) {
        WalkPhase.MOVING -> Motion.FLYING
        WalkPhase.PAUSED -> Motion.HOVERING
        else -> Motion.STILL
    }
}
