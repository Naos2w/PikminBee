package com.naos.pikminbee.domain

/** Route preview/launch from the current device or parked virtual position.
 * The first user-added waypoint is always the first destination, never the origin.
 */
object FloatingRouteDraft {
    fun anchors(origin: Coordinate?, stops: List<Coordinate>, loop: Boolean): List<Coordinate> {
        require(origin != null) { "找不到目前位置；請先取得精確定位或在地圖設定位置" }
        require(stops.size in 1..14) { "請先新增 1 至 14 個路點" }
        val points = buildList {
            add(origin)
            stops.forEach { point ->
                if (WalkingGeometry.distanceMeters(last(), point) > 1.0) add(point)
            }
        }
        require(points.size >= 2) { "所有路點都和目前位置重疊" }
        if (loop && WalkingGeometry.distanceMeters(points.last(), origin) > 1.0) {
            return points + origin
        }
        return points
    }
}
