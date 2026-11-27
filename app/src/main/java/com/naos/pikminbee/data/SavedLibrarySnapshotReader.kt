package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Read-only validation of the complete persisted library. Unlike display
 * readers, no invalid record may be silently skipped when preparing a backup.
 */
internal object SavedLibrarySnapshotReader {
    fun read(placesJson: String?, routesJson: String?): SavedDataBackupCodec.Snapshot {
        val places = records(placesJson, 10_000).map { item ->
            requireFields(item, setOf("id", "name", "lat", "lon", "countryCode",
                "countryName", "city", "timeZoneId", "createdAt"))
            val id = integer(item, "id")
            SavedPlace(
                id = id,
                name = string(item, "name"),
                coordinate = point(item),
                countryCode = optional(item, "countryCode"),
                countryName = optional(item, "countryName"),
                city = optional(item, "city"),
                timeZoneId = optional(item, "timeZoneId"),
                createdAtMillis = if (item.has("createdAt")) integer(item, "createdAt") else id,
            )
        }
        val routes = records(routesJson, 100).map { item ->
            requireFields(item, setOf("id", "name", "mode", "points", "createdAt"))
            val points = item.get("points") as? JSONArray
                ?: throw IllegalArgumentException("Invalid route points")
            require(points.length() in 1..15)
            SavedRoute(
                id = string(item, "id"),
                name = string(item, "name"),
                mode = WalkMode.valueOf(string(item, "mode")),
                stops = List(points.length()) { i ->
                    val record = points.getJSONObject(i)
                    requireFields(record, setOf("lat", "lon"))
                    point(record)
                },
                createdAtMillis = if (item.has("createdAt")) integer(item, "createdAt") else 0L,
            )
        }
        val snapshot = SavedDataBackupCodec.Snapshot(places, routes)
        // Enforce the same field, ID, size and encoding bounds as the backup writer.
        SavedDataBackupCodec.encode(snapshot)
        return snapshot
    }

    private fun records(raw: String?, max: Int): List<JSONObject> {
        if (raw == null) return emptyList()
        require(raw.length <= SavedDataBackupCodec.MAX_BYTES)
        val tokener = JSONTokener(raw)
        val array = tokener.nextValue() as? JSONArray
            ?: throw IllegalArgumentException("Invalid library array")
        require(tokener.nextClean() == '\u0000') { "Unexpected trailing library content" }
        require(array.length() <= max) { "Library capacity exceeded" }
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun requireFields(item: JSONObject, supported: Set<String>) {
        require(item.keys().asSequence().all { it in supported }) { "Unknown library field" }
    }

    private fun string(item: JSONObject, key: String): String =
        item.get(key) as? String ?: throw IllegalArgumentException("Invalid library text")

    private fun integer(item: JSONObject, key: String): Long = when (val value = item.get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("Invalid library integer")
    }

    private fun optional(item: JSONObject, key: String): String? {
        if (!item.has(key) || item.isNull(key)) return null
        // Match the existing legacy display reader's null/blank convention.
        return string(item, key).takeUnless { it.isBlank() || it.equals("null", true) }
    }

    private fun number(item: JSONObject, key: String): Double =
        (item.get(key) as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Invalid library coordinate")

    private fun point(item: JSONObject) = Coordinate(number(item, "lat"), number(item, "lon"))
}
