package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.FlowerMapDraft
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkingGeometry

/**
 * Shared server-side route guard. Never trust the LAN browser's disabled buttons:
 * requests must satisfy the same limits as the native editor.
 */
object LanRouteDraftPolicy {
    const val MAX_ROUTE_STOPS = 15

    data class Addition(
        val stops: List<Coordinate>,
        val accepted: Boolean,
        val message: String,
    )

    fun add(
        mode: WalkMode,
        current: List<Coordinate>,
        candidate: Coordinate,
        origin: Coordinate?,
    ): Addition = when (mode) {
        WalkMode.ROUTE -> {
            if (current.size >= MAX_ROUTE_STOPS) reject(current, "一般道路路線最多 15 點")
            else Addition(current + candidate, true, "已新增道路路點")
        }
        WalkMode.FLOWERS -> when {
            origin == null -> reject(current, "請先在手機啟動模擬定位，再從遠端新增尋花路點")
            current.size > FlowerMapDraft.MAX_STOPS ->
                reject(current, "舊版尋花草稿超過 14 點，請先移除多餘路點")
            else -> {
                val result = FlowerMapDraft.add(current, origin, candidate)
                when (result.result) {
                    FlowerMapDraft.AddResult.ADDED ->
                        Addition(result.stops, true, "已新增尋花路點")
                    FlowerMapDraft.AddResult.DUPLICATE ->
                        reject(current, "25 公尺內已有尋花路點，未重複新增")
                    FlowerMapDraft.AddResult.AT_START ->
                        reject(current, "選取位置太接近行走起點")
                    FlowerMapDraft.AddResult.FULL ->
                        reject(current, "尋花路線最多 14 點")
                }
            }
        }
    }

    fun anchors(
        mode: WalkMode,
        origin: Coordinate,
        stops: List<Coordinate>,
        loop: Boolean,
    ): List<Coordinate> {
        if (mode == WalkMode.FLOWERS) return FlowerMapDraft.routePoints(origin, stops, loop)
        require(stops.size <= MAX_ROUTE_STOPS) { "一般道路路線最多 15 點" }
        // There is only room for 16 Valhalla anchors, including a loop return.
        require(!loop || stops.size <= 14) { "循環道路路線最多 14 個路點" }
        val points = buildList {
            add(origin)
            stops.forEach {
                if (WalkingGeometry.distanceMeters(last(), it) > 1.0) add(it)
            }
            if (loop && size >= 2 &&
                WalkingGeometry.distanceMeters(last(), origin) > 1.0) add(origin)
        }
        require(points.size in 2..16) { "請選取至少一個不同於起點的路點" }
        return points
    }

    private fun reject(stops: List<Coordinate>, message: String) =
        Addition(stops, false, message)
}
