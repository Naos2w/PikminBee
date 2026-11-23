package com.naos.pikminbee.ui

import java.text.NumberFormat
import java.util.Locale

/**
 * A floating control shows the best available daily step source, never a sum of
 * overlapping Health Connect, hardware sensor and virtual-session counters.
 */
internal object FloatingDailyStepsPolicy {
    fun render(
        healthConnectToday: Long?,
        localSensorToday: Long?,
        currentVirtualWalk: Long?,
    ): String {
        fun formatted(value: Long): String =
            NumberFormat.getIntegerInstance(Locale.TAIWAN).format(value.coerceAtLeast(0))
        val daily = when {
            healthConnectToday != null && healthConnectToday >= 0 ->
                "今日總步數 ${formatted(healthConnectToday)} 步（HC 彙總）"
            localSensorToday != null && localSensorToday >= 0 ->
                "今日已記錄 ${formatted(localSensorToday)} 步（手機）"
            else -> "今日步數 —（尚未取得）"
        }
        return if (currentVirtualWalk != null)
            "$daily\n本次虛擬估算 ${formatted(currentVirtualWalk)} 步"
        else daily
    }
}
