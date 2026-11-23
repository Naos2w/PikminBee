package com.naos.pikminbee.domain

/** Route lifecycle is shared by Android, the floating overlay and paired Wi-Fi. */
enum class RouteLifecycleChange {
    START, ARRIVE, PAUSE, RESUME, MANUAL_STOP, CLEAR, TELEPORT,
    RESTORE_REAL_GPS, RESTORE_VIRTUAL_GPS, SHOW_OVERLAY, HIDE_OVERLAY,
    PROVIDER_FAILURE, SYSTEM_SERVICE_STOP,
}

/**
 * Pure presentation / notification rules. An overlay visibility action must
 * never erase the real route status that the App and paired browser also read.
 */
object RouteSurfaceStatusPolicy {
    fun movingStatus(mode: WalkMode?): String =
        if (mode == WalkMode.FLOWERS) "尋花步行中" else "沿道路步行中"

    fun afterOverlayChange(
        currentStatus: String,
        phase: WalkPhase,
        mode: WalkMode?,
        mockActive: Boolean,
        visible: Boolean,
    ): String {
        if (phase == WalkPhase.PARKED ||
            (mode != null && phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED))) {
            return currentStatus
        }
        return if (visible) {
            if (mockActive) "懸浮蜜蜂已開啟 · 可操作路徑"
            else "懸浮蜜蜂已開啟 · 定位測試尚未啟動"
        } else {
            if (mockActive) "懸浮控制已關閉 · 模擬定位保持"
            else "懸浮控制已關閉"
        }
    }

    /**
     * Paused route speed means the selected resume pace, not a real sensor reading.
     * PARKED and IDLE must not expose a stale moving speed to paired Wi-Fi Web.
     */
    fun visibleRouteSpeedKmh(phase: WalkPhase, selectedSpeedKmh: Double): Double =
        if (phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED) &&
            selectedSpeedKmh.isFinite() && selectedSpeedKmh >= 0.0) selectedSpeedKmh
        else 0.0

    fun parkedOverlaySummary(status: String, mode: WalkMode?): String = when {
        mode != null && status.startsWith("已抵達終點") -> "已抵達終點 · 原地停留"
        mode != null && status.startsWith("步行已停止") -> "已停止 · 原地停留"
        mode != null -> status.ifBlank { "已停止 · 原地停留" }.take(32)
        else -> status.ifBlank { "原地停留" }.take(32)
    }

    /** A previous journey's completion notification must not outlive a new action. */
    fun clearsPreviousArrival(change: RouteLifecycleChange): Boolean = when (change) {
        RouteLifecycleChange.START,
        RouteLifecycleChange.MANUAL_STOP,
        RouteLifecycleChange.CLEAR,
        RouteLifecycleChange.TELEPORT,
        RouteLifecycleChange.RESTORE_REAL_GPS,
        RouteLifecycleChange.RESTORE_VIRTUAL_GPS -> true
        RouteLifecycleChange.ARRIVE,
        RouteLifecycleChange.PAUSE,
        RouteLifecycleChange.RESUME,
        RouteLifecycleChange.SHOW_OVERLAY,
        RouteLifecycleChange.HIDE_OVERLAY,
        RouteLifecycleChange.PROVIDER_FAILURE,
        RouteLifecycleChange.SYSTEM_SERVICE_STOP -> false
    }
}
