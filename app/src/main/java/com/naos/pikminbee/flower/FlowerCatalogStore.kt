package com.naos.pikminbee.flower

import android.content.Context
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** In-process state also keeps the phone UI synchronised after file import. */
object FlowerCatalogState {
    val spots = MutableStateFlow<List<FlowerSpot>>(emptyList())
    private var initialized = false

    @Synchronized fun initialize(items: List<FlowerSpot>) {
        if (initialized) return
        spots.value = items
        initialized = true
    }
}

/** Local-only user observations and explicitly unverified imported candidates. */
class FlowerCatalogStore(context: Context) {
    private val preferences = context.getSharedPreferences("pikminbee_flower_catalog", Context.MODE_PRIVATE)

    init {
        FlowerCatalogState.initialize(read())
    }

    fun all(): List<FlowerSpot> = FlowerCatalogState.spots.value

    fun addManual(name: String, point: Coordinate): FlowerSpot = synchronized(FlowerCatalogState) {
        val current = all()
        val old = current.firstOrNull {
            WalkingGeometry.distanceMeters(it.coordinate, point) <= 25.0
        }
        if (old != null) {
            val updated = old.copy(
                name = name.trim().take(60).ifBlank { old.name },
                evidence = FlowerEvidence.PERSONALLY_CHECKED,
                recordedAtMillis = System.currentTimeMillis(),
            )
            persist(current.map { if (it.id == old.id) updated else it })
            updated
        } else {
            val spot = FlowerSpot.manual(name.trim(), point)
            require(current.size < 1_000) { "花點庫已達 1,000 筆上限" }
            persist(current + spot)
            spot
        }
    }

    /** Store manually reviewed pixel-calibrated candidates as UNVERIFIED.
     *  Never silently upgrade an existing unverified or confirmed flower. */
    fun addUnverifiedCandidate(name: String, point: Coordinate): Boolean =
        synchronized(FlowerCatalogState) {
            val current = all()
            if (current.size >= 1_000 || current.any {
                    WalkingGeometry.distanceMeters(it.coordinate, point) <= 25.0
                }) return@synchronized false
            val spot = FlowerSpot.imported(name, point)
            persist(current + spot)
            true
        }

    fun importCandidates(imported: FlowerImport): FlowerMerge = synchronized(FlowerCatalogState) {
        val result = FlowerFinder.combine(all(), imported.spots)
        persist(result.flowers)
        result
    }

    fun markChecked(id: String): Boolean = synchronized(FlowerCatalogState) {
        val current = all()
        if (current.none { it.id == id }) return@synchronized false
        persist(current.map {
            if (it.id == id) it.copy(
                evidence = FlowerEvidence.PERSONALLY_CHECKED,
                recordedAtMillis = System.currentTimeMillis(),
            ) else it
        })
        true
    }

    fun remove(id: String): Boolean = synchronized(FlowerCatalogState) {
        val current = all()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return@synchronized false
        persist(next)
        true
    }

    private fun persist(next: List<FlowerSpot>) {
        require(next.size <= 1_000)
        val records = JSONArray()
        next.forEach {
            records.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("lat", it.coordinate.latitude).put("lon", it.coordinate.longitude)
                .put("evidence", it.evidence.name).put("recordedAt", it.recordedAtMillis))
        }
        check(preferences.edit().putString("spots", records.toString()).commit()) {
            "花點資料無法寫入裝置儲存空間"
        }
        FlowerCatalogState.spots.value = next
    }

    private fun read(): List<FlowerSpot> = runCatching {
        val records = JSONArray(preferences.getString("spots", "[]") ?: "[]")
        if (records.length() > 1_000) return@runCatching emptyList()
        (0 until records.length()).mapNotNull { index ->
            runCatching {
                val row = records.getJSONObject(index)
                FlowerSpot(
                    id = row.getString("id"),
                    name = row.getString("name"),
                    coordinate = Coordinate(row.getDouble("lat"), row.getDouble("lon")),
                    evidence = FlowerEvidence.valueOf(row.getString("evidence")),
                    recordedAtMillis = row.getLong("recordedAt"),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
