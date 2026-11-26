package com.naos.pikminbee.ui

import java.text.NumberFormat
import java.util.Locale

/** Route estimate only: never display a health or activity total. */
internal object FloatingDailyStepsPolicy {
    fun render(currentVirtualWalk: Long?): String {
        if (currentVirtualWalk == null) return ""
        val formatted = NumberFormat.getIntegerInstance(Locale.TAIWAN)
            .format(currentVirtualWalk.coerceAtLeast(0))
        return "本次路線估算 $formatted 步（非實際運動）"
    }
}
