package com.naos.pikminbee.route

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Rate-limited community Valhalla service: no SLA and no straight-line fallback. */
object ValhallaPlanner {
    class RouteException(message: String) : Exception(message)
    private const val ENDPOINT = "https://valhalla1.openstreetmap.de/route"
    private const val MAX_RESPONSE_BYTES = 3_000_000
    // Valhalla may correlate a tap in a park or courtyard to the closest walkable edge.
    // Never silently choose an arbitrarily distant road.
    internal const val MAX_SNAP_METERS = 200.0
    /** Includes actual distance from each picked waypoint to the routed path. */
    data class RoadPlan(
        val geometry: List<Coordinate>,
        val snapMetres: List<Double>,
    ) {
        val maxSnapMetres: Double get() = snapMetres.maxOrNull() ?: 0.0
        val originSnapMetres: Double get() = snapMetres.firstOrNull() ?: 0.0
    }

    private val cache = LinkedHashMap<String, Pair<Long, RoadPlan>>()
    private var lastRequestMillis = 0L

    suspend fun plan(points: List<Coordinate>): List<Coordinate> = planDetailed(points).geometry

    suspend fun planDetailed(points: List<Coordinate>): RoadPlan = withContext(Dispatchers.IO) {
        if (points.size !in 2..16) throw RouteException("請選擇至少兩個、最多十六個路線點")
        if (WalkingGeometry.lengthMeters(points) > 40_000.0) throw RouteException("路線超過 40 公里")
        val key = points.joinToString("|") {
            String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
        }
        synchronized(cache) {
            cache[key]?.takeIf { System.currentTimeMillis() - it.first < 3_600_000L }?.second
        }?.let { return@withContext it }
        synchronized(cache) {
            val now = System.currentTimeMillis()
            if (now - lastRequestMillis < 2_000L) throw RouteException("道路查詢過於頻繁，請稍後重試")
            lastRequestMillis = now
        }
        val locations = JSONArray()
        points.forEach { point ->
            locations.put(JSONObject().put("lat", point.latitude).put("lon", point.longitude)
                .put("radius", MAX_SNAP_METERS.toInt()).put("rank_candidates", true))
        }
        val json = JSONObject().put("locations", locations)
            .put("costing", "pedestrian").put("directions_type", "none")
            .put("shape_format", "polyline6").put("units", "km").toString()
        val result = parseDetailedResponse(post(json), points)
        synchronized(cache) {
            cache[key] = System.currentTimeMillis() to result
            while (cache.size > 8) cache.remove(cache.keys.first())
        }
        result
    }

    private fun post(json: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 18_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PikminBee/0.2 (private pedestrian routing)")
        }
        return try {
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status != 200) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText().take(180) }.orEmpty()
                throw RouteException("道路服務 HTTP $status：$detail")
            }
            conn.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val block = ByteArray(8192)
                while (true) {
                    val n = stream.read(block)
                    if (n == -1) break
                    if (output.size() + n > MAX_RESPONSE_BYTES) throw RouteException("道路資料太大")
                    output.write(block, 0, n)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (e: RouteException) {
            throw e
        } catch (e: Exception) {
            throw RouteException("道路查詢失敗：" + (e.message ?: "網路無法連線"))
        } finally {
            conn.disconnect()
        }
    }

    fun parseResponse(response: String, stops: List<Coordinate>): List<Coordinate> =
        parseDetailedResponse(response, stops).geometry

    fun parseDetailedResponse(response: String, stops: List<Coordinate>): RoadPlan {
        val body = JSONObject(response)
        val legs = body.optJSONObject("trip")?.optJSONArray("legs")
            ?: throw RouteException("道路服務未提供完整路線")
        if (legs.length() != stops.size - 1) throw RouteException("部分路點無法規劃道路")
        val result = ArrayList<Coordinate>()
        val snapDistances = MutableList(stops.size) { 0.0 }
        repeat(legs.length()) { leg ->
            val vertices = decodePolyline6(legs.getJSONObject(leg).optString("shape"))
            if (vertices.size < 2) throw RouteException("路線缺少有效節點")
            val snapStart = WalkingGeometry.distanceMeters(vertices.first(), stops[leg])
            val snapEnd = WalkingGeometry.distanceMeters(vertices.last(), stops[leg + 1])
            if (snapStart > MAX_SNAP_METERS || snapEnd > MAX_SNAP_METERS) {
                throw RouteException("附近 200 公尺內找不到可步行的道路或步道，請稍微調整路點")
            }
            // At a junction, adjacent legs may snap differently. Show the
            // larger measured offset instead of hiding the discrepancy.
            snapDistances[leg] = maxOf(snapDistances[leg], snapStart)
            snapDistances[leg + 1] = maxOf(snapDistances[leg + 1], snapEnd)
            vertices.forEach { point ->
                if (result.isEmpty() || WalkingGeometry.distanceMeters(result.last(), point) > 0.30) {
                    result.add(point)
                }
                if (result.size > 10_000) throw RouteException("路線節點過多，請縮短路線")
            }
        }
        if (result.size < 2) throw RouteException("無法產生步行路線")
        return RoadPlan(result, snapDistances)
    }

    /** Valhalla uses 6-decimal encoded polylines, not Google's 5-decimal default. */
    fun decodePolyline6(encoded: String): List<Coordinate> {
        val points = ArrayList<Coordinate>()
        var index = 0
        var lat = 0L
        var lon = 0L
        fun delta(): Long {
            var shift = 0
            var value = 0L
            while (true) {
                if (index >= encoded.length || shift > 60) throw RouteException("道路座標格式錯誤")
                val code = encoded[index++].code - 63
                if (code !in 0..63) throw RouteException("道路編碼內容錯誤")
                value = value or ((code.toLong() and 31L) shl shift)
                shift += 5
                if (code and 32 == 0) break
            }
            return if (value and 1L == 1L) (value shr 1).inv() else value shr 1
        }
        while (index < encoded.length) {
            lat += delta()
            lon += delta()
            points.add(runCatching {
                Coordinate(lat / 1_000_000.0, lon / 1_000_000.0)
            }.getOrElse { throw RouteException("道路座標超出範圍") })
            if (points.size > 10_000) throw RouteException("路線節點太多")
        }
        return points
    }
}
