package com.naos.pikminbee.ui

import com.naos.pikminbee.health.RealStepSource

/** A display policy, not a health recorder: overlapping totals are never added. */
internal object HealthDashboardPolicy {
    data class DailyMetric(val title: String, val count: Long?, val sourceLabel: String)

    fun dailyMetric(hcCount: Long?, sensorCount: Long?, source: RealStepSource): DailyMetric {
        if (hcCount != null && hcCount >= 0L)
            return DailyMetric("今日總步數", hcCount, "Health Connect 彙總")
        if (source == RealStepSource.PHONE_STEP_SENSOR && sensorCount != null && sensorCount >= 0L)
            return DailyMetric("本機今日步數", sensorCount, "手機感測器 · 非 HC 總數")
        return DailyMetric("今日總步數", null, "尚未取得步數")
    }

    fun sourceLabel(source: RealStepSource): String = when (source) {
        RealStepSource.HC_ON_DEVICE -> "Health Connect 原生計步"
        RealStepSource.PHONE_STEP_SENSOR -> "手機感測器"
        RealStepSource.UNAVAILABLE -> "尚無可用計步來源"
    }

    fun syncLabel(source: RealStepSource, enabled: Boolean, writeAllowed: Boolean,
                  providerAvailable: Boolean, pending: Int, busy: Boolean): String = when {
        busy -> "同步中…"
        source == RealStepSource.HC_ON_DEVICE -> "系統原生計步，不重複寫入"
        source != RealStepSource.PHONE_STEP_SENSOR || !providerAvailable -> "目前無法同步"
        !writeAllowed -> "尚未授權寫入"
        !enabled -> "已關閉自動同步"
        pending > 0 -> "待同步 ${pending} 筆"
        else -> "已開啟 · 無待同步紀錄"
    }
}
