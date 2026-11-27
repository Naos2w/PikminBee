package com.naos.pikminbee.ui

import java.text.NumberFormat
import java.util.Locale

/** Display independently sourced counts; never add overlapping health sources or estimates. */
internal object FloatingDailyStepsPolicy {
    fun render(currentVirtualWalk: Long?, healthDisplay: Boolean = false,
        hcAggregate: Long? = null, sensorCount: Long? = null): String {
        fun number(value: Long) = NumberFormat.getIntegerInstance(Locale.TAIWAN).format(value.coerceAtLeast(0))
        val daily = if (!healthDisplay) null else when {
            hcAggregate != null && hcAggregate >= 0 -> "今日總步數 ${number(hcAggregate)} 步（HC 彙總）"
            sensorCount != null && sensorCount >= 0 -> "今日已記錄 ${number(sensorCount)} 步（手機）"
            else -> "今日步數 —（尚未取得）"
        }
        val route = currentVirtualWalk?.let { "本次路線估算 ${number(it)} 步（非實際運動）" }
        return listOfNotNull(daily, route).joinToString("\n")
    }
}
