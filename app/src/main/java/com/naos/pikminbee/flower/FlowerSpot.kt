package com.naos.pikminbee.flower

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry
import java.util.UUID

enum class FlowerEvidence { PERSONALLY_CHECKED, UNVERIFIED_IMPORT }

data class FlowerSpot(
    val id: String,
    val name: String,
    val coordinate: Coordinate,
    val evidence: FlowerEvidence,
    val recordedAtMillis: Long,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank() && name.length <= 60) { "花點名稱必須為 1 至 60 個字" }
        require(recordedAtMillis > 0)
    }

    companion object {
        fun manual(name: String, point: Coordinate, now: Long = System.currentTimeMillis()): FlowerSpot =
            FlowerSpot(UUID.randomUUID().toString(), name.trim(), point, FlowerEvidence.PERSONALLY_CHECKED, now)

        fun imported(name: String, point: Coordinate, now: Long = System.currentTimeMillis()): FlowerSpot =
            FlowerSpot(UUID.randomUUID().toString(), name.trim().ifBlank { "未命名候選花點" }.take(60),
                point, FlowerEvidence.UNVERIFIED_IMPORT, now)
    }
}

data class FlowerMerge(val flowers: List<FlowerSpot>, val added: Int, val duplicates: Int)

/** UI-only candidate finder. Distances are geographic approximations, not Valhalla walking distances. */
object FlowerFinder {
    private const val DUPLICATE_RADIUS_METRES = 25.0

    fun nearby(
        origin: Coordinate,
        flowers: List<FlowerSpot>,
        radiusMetres: Double,
        onlyChecked: Boolean = false,
        maxCount: Int = 100,
    ): List<FlowerSpot> {
        require(radiusMetres.isFinite() && radiusMetres in 1.0..50_000.0)
        require(maxCount in 1..1_000)
        return flowers.asSequence()
            .filter { !onlyChecked || it.evidence == FlowerEvidence.PERSONALLY_CHECKED }
            .map { it to WalkingGeometry.distanceMeters(origin, it.coordinate) }
            .filter { it.second <= radiusMetres }
            .sortedWith(compareBy<Pair<FlowerSpot, Double>> { it.second }.thenBy { it.first.id })
            .take(maxCount)
            .map { it.first }
            .toList()
    }

    /** Adds only as many ordered stops as are safe for an optional closed pedestrian circuit. */
    fun appendToDraft(
        origin: Coordinate,
        draft: List<Coordinate>,
        candidates: List<FlowerSpot>,
        maxStops: Int = 14,
    ): List<Coordinate> {
        require(maxStops in 1..14)
        require(draft.size <= maxStops) { "目前路點已達上限；不會自動刪除既有路點" }
        val next = draft.toMutableList()
        for (spot in candidates) {
            if (next.size >= maxStops) break
            val point = spot.coordinate
            if (WalkingGeometry.distanceMeters(origin, point) < 2.0) continue
            if (next.any { WalkingGeometry.distanceMeters(it, point) <= DUPLICATE_RADIUS_METRES }) continue
            next.add(point)
        }
        return next
    }

    fun combine(existing: List<FlowerSpot>, incoming: List<FlowerSpot>, maxStored: Int = 1_000): FlowerMerge {
        require(maxStored > 0)
        val result = existing.toMutableList()
        var added = 0
        var duplicates = 0
        for (spot in incoming) {
            if (result.any {
                    WalkingGeometry.distanceMeters(it.coordinate, spot.coordinate) <= DUPLICATE_RADIUS_METRES
                }) {
                duplicates++
                continue
            }
            if (result.size >= maxStored) throw IllegalArgumentException("最多可保存 $maxStored 個花點")
            result.add(spot)
            added++
        }
        // Import never upgrades an unverified location into an authenticated game location.
        return FlowerMerge(result, added, duplicates)
    }
}
