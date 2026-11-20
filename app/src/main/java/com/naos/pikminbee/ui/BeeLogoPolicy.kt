package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkPhase

/** Product-specific honey/green accents over a fixed Mineral + Regular palette. */
internal object BeeLogoPolicy {
    const val HONEY = 0xFFD9AC4E.toInt()
    const val MOVING = 0xFF78A58C.toInt()
    const val PAUSED = 0xFFE2BB71.toInt()
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
