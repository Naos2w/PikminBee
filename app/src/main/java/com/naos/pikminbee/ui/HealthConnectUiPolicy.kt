package com.naos.pikminbee.ui

/**
 * Presentation-only Health Connect states. Permissions and persisted consent
 * remain the responsibility of the existing Health Connect/ledger services.
 */
internal object HealthConnectUiPolicy {
    fun todayValue(canRead: Boolean, count: Long?): String =
        if (canRead && count != null) count.toString() else "—"

    fun syncLabel(
        available: Boolean,
        optedIn: Boolean,
        canWrite: Boolean,
        pending: Int,
        lastFailed: Boolean,
    ): String = when {
        !available -> "不可使用"
        !optedIn -> "未啟用"
        !canWrite -> "等待寫入授權"
        lastFailed -> "最近同步失敗"
        pending > 0 -> "待同步 $pending 筆"
        else -> "同步已開啟"
    }

    fun canSyncNow(
        available: Boolean,
        optedIn: Boolean,
        canWrite: Boolean,
        pending: Int,
    ): Boolean = available && optedIn && canWrite && pending > 0
}
