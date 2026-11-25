package com.naos.pikminbee.lan

/** Shared state derived from service, pending approval, token lifetime and poll activity. */
enum class LanConnectionPhase(val label: String) {
    CLOSED("Wi-Fi 關閉"),
    STARTING("Wi-Fi 啟動中"),
    WAITING_PAIR("等待配對"),
    PENDING_APPROVAL("待核准"),
    CONNECTED("已連線"),
    OFFLINE("暫時離線"),
    EXPIRED("已到期 · 重新配對"),
    REVOKED("已撤銷 · 重新配對"),
    ERROR("Wi-Fi 錯誤"),
}

const val LAN_ONLINE_WINDOW_MILLIS = 15_000L

data class LanConnection(
    val phase: LanConnectionPhase = LanConnectionPhase.CLOSED,
    val remainingSeconds: Int = 0,
    val device: String = "",
    val pendingDevice: String = "",
    val awaitingBrowser: Boolean = false,
    // Local-only request identity; not a bearer token or PIN.
    val approvalId: String = "",
    val renewal: Boolean = false,
) {
    val hasSession: Boolean get() = remainingSeconds > 0
    val showPin: Boolean get() = !hasSession && !awaitingBrowser && phase in listOf(
        LanConnectionPhase.WAITING_PAIR, LanConnectionPhase.PENDING_APPROVAL,
        LanConnectionPhase.EXPIRED, LanConnectionPhase.REVOKED)
    val countdown: String get() = String.format(java.util.Locale.US, "%02d:%02d:%02d",
        remainingSeconds / 3600, remainingSeconds % 3600 / 60, remainingSeconds % 60)
    val label: String get() = if (awaitingBrowser) "已核准 · 等待瀏覽器"
        else phase.label
}
