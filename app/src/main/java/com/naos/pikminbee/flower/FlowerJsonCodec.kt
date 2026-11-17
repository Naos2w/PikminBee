package com.naos.pikminbee.flower

import com.naos.pikminbee.domain.Coordinate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class FlowerImport(val spots: List<FlowerSpot>, val ignored: Int, val source: String)

/**
 * User-supplied, explicitly flower-tagged GeoJSON Point features or community-style JSON rows.
 * No internet scraping or game API; imported data always stays UNVERIFIED.
 */
object FlowerJsonCodec {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_IMPORT_ROWS = 1000
    class ImportException(message: String) : IllegalArgumentException(message)

    fun decode(stream: InputStream): FlowerImport = decode(readBounded(stream))

    fun decode(bytes: ByteArray): FlowerImport {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES)
            throw ImportException("檔案為空或超過 1 MB")
        val text = bytes.toString(Charsets.UTF_8).trim()
        val items: JSONArray
        val geoJson: Boolean
        try {
            if (text.startsWith("{")) {
                val root = JSONObject(text)
                if (root.optString("type") != "FeatureCollection")
                    throw ImportException("GeoJSON 必須是 FeatureCollection")
                items = root.getJSONArray("features")
                geoJson = true
            } else if (text.startsWith("[")) {
                items = JSONArray(text)
                geoJson = false
            } else throw ImportException("只支援 GeoJSON FeatureCollection 或花點 JSON 陣列")
        } catch (e: ImportException) {
            throw e
        } catch (_: JSONException) {
            throw ImportException("JSON 格式無效或缺少 features")
        }
        if (items.length() > MAX_IMPORT_ROWS)
            throw ImportException("單次最多匯入 $MAX_IMPORT_ROWS 筆，請先拆分資料")
        val result = ArrayList<FlowerSpot>()
        var ignored = 0
        for (index in 0 until items.length()) {
            val row = items.optJSONObject(index)
            if (row == null) {
                ignored++
                continue
            }
            val info = if (geoJson) row.optJSONObject("properties") ?: JSONObject() else row
            val category = listOf("kind", "type", "category")
                .mapNotNull { info.optString(it).takeIf(String::isNotBlank) }
            val allowed = category.any {
                it.trim().lowercase() in setOf(
                    "flower", "flower_spot", "big_flower", "bigflower", "花點", "大花", "大花點"
                )
            }
            if (!allowed) {
                ignored++ // E.g. mushroom POIs must never be silently treated as flowers.
                continue
            }
            val name = info.optString("name").trim().take(60).ifBlank { "未命名候選花點" }
            val coordinates = if (geoJson) {
                val geometry = row.optJSONObject("geometry")
                if (row.optString("type") != "Feature" ||
                    geometry?.optString("type") != "Point")
                    throw ImportException("第 ${index + 1} 筆花點必須是 GeoJSON Point")
                val arr = geometry.optJSONArray("coordinates")
                    ?: throw ImportException("第 ${index + 1} 筆缺少 GeoJSON 座標")
                if (arr.length() < 2) throw ImportException("第 ${index + 1} 筆經緯度不足")
                arr.optDouble(1, Double.NaN) to arr.optDouble(0, Double.NaN)
            } else {
                val lat = row.optDouble("lat", Double.NaN)
                val lon = if (row.has("lng")) row.optDouble("lng", Double.NaN)
                    else row.optDouble("lon", Double.NaN)
                lat to lon
            }
            val point = try {
                Coordinate(coordinates.first, coordinates.second)
            } catch (_: IllegalArgumentException) {
                throw ImportException("第 ${index + 1} 筆經緯度無效或超出範圍")
            }
            result.add(FlowerSpot.imported(name, point))
        }
        if (result.isEmpty())
            throw ImportException("沒有明確標記為花點的有效資料；不會將其他地標當成大花")
        return FlowerImport(result, ignored, if (geoJson) "GeoJSON" else "JSON")
    }

    fun encodeGeoJson(spots: List<FlowerSpot>): ByteArray {
        require(spots.size <= MAX_IMPORT_ROWS)
        val features = JSONArray()
        for (spot in spots) {
            val properties = JSONObject()
                .put("name", spot.name)
                .put("kind", "big_flower")
                // This is user data. Never claim official / live verification on export.
                .put("evidence", if (spot.evidence == FlowerEvidence.PERSONALLY_CHECKED)
                    "self-reported" else "unverified")
            val point = JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(spot.coordinate.longitude).put(spot.coordinate.latitude))
            features.put(JSONObject()
                .put("type", "Feature")
                .put("properties", properties)
                .put("geometry", point))
        }
        val result = JSONObject().put("type", "FeatureCollection").put("features", features)
        return result.toString(2).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "花點資料過大，無法匯出單一檔案" }
        }
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw ImportException("花點資料超過 1 MB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
