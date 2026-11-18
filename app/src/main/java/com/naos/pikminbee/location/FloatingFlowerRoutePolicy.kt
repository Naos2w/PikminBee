package com.naos.pikminbee.location

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.FlowerMapDraft
import com.naos.pikminbee.domain.WalkPhase

/**
 * Only an explicit overlay click may request a saved flower circuit.
 * This prepares anchors; it never changes the mock location or makes network calls.
 */
object FloatingFlowerRoutePolicy {
    // Speed comes from the shared persisted WalkSpeedState when the route starts.

    data class Decision(
        val anchors: List<Coordinate> = emptyList(),
        val problem: String? = null,
    ) {
        val allowed: Boolean get() = problem == null
    }

    fun prepare(
        mockActive: Boolean,
        phase: WalkPhase,
        origin: Coordinate?,
        flowerStops: List<Coordinate>,
    ): Decision {
        if (!mockActive || origin == null) return Decision(problem = "請先在手機啟用模擬定位")
        if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED)
            return Decision(problem = "請先停止目前的行走路線")
        if (flowerStops.isEmpty()) return Decision(problem = "請先在尋花頁面加入至少一個路點")
        val points = runCatching { FlowerMapDraft.routePoints(origin, flowerStops, loop = true) }
            .getOrElse { return Decision(problem = it.message ?: "尋花路線不正確") }
        return Decision(anchors = points)
    }
}
