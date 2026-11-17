package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import java.util.UUID

/** A named set of ordered stops, never an already-routed Valhalla geometry. */
data class SavedRoute(
    val id: String,
    val name: String,
    val mode: WalkMode,
    val stops: List<Coordinate>,
    val createdAtMillis: Long,
) {
    init {
        require(stops.size in 1..15) { "路線需包含 1 到 15 個路點" }
        require(name.isNotBlank() && name.length <= 60) { "路線名稱長度需為 1 到 60 字" }
        require(id.isNotBlank()) { "路線 ID 不可空白" }
    }

    fun fileName(): String {
        val safe = name.replace(Regex("[\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim(' ', '.').take(48).ifBlank { "PikminBee-route" }
        return "$safe.gpx"
    }

    companion object {
        fun create(name: String, mode: WalkMode, stops: List<Coordinate>): SavedRoute =
            SavedRoute(
                id = UUID.randomUUID().toString(),
                name = name.trim().take(60),
                mode = mode,
                stops = stops.toList(),
                createdAtMillis = System.currentTimeMillis(),
            )
    }
}
