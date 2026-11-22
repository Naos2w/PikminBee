package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.domain.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Explicit-save-only place suggestions. Sends a user-selected coordinate to
 * Photon (OSM) and TimeAPI over HTTPS at most once per cache miss; never on
 * GPS ticks, never on startup and never as a hidden background service.
 *
 * Public demo endpoints are best-effort, throttle/availability not guaranteed.
 * Failure is nonfatal: names fall back to coordinates, zones stay unknown.
 */
class PlaceLookup(context: Context) {
    private val cache = context.applicationContext.getSharedPreferences(
        "pikminbee_place_lookup", Context.MODE_PRIVATE)
    private val ttlMillis = 30L * 24 * 60 * 60 * 1000

    suspend fun suggest(point: Coordinate): PlaceSuggestion = withContext(Dispatchers.IO) {
        val key = cacheKey(point)
        cache.getString("suggest:$key", null)?.let { readCache(it) }?.let { cached ->
            // A successful POI lookup may have failed to resolve the time zone.
            // Retry zone resolution on the next explicit save rather than hiding
            // destination time for the full 30-day Photon name-cache lifetime.
            if (cached.timeZoneId != null) return@withContext cached
            return@withContext cached.copy(timeZoneId = resolveZoneCached(point))
        }
        val raw = fetchJson(
            "https://photon.komoot.io/reverse?lat=" + "%.6f".format(Locale.US, point.latitude) +
                "&lon=" + "%.6f".format(Locale.US, point.longitude) +
                "&radius=0.3&limit=8&lang=zh"
        )
        val records = parsePhoton(raw)
        val base = PlaceSuggestionPolicy.nameFor(point, records)
        val suggestion = base.copy(timeZoneId = resolveZoneCached(point))
        // Do not cache network failures for a month; allow retry on the next save.
        if ((raw?.optJSONArray("features")?.length() ?: 0) > 0) {
            cache.edit().putString("suggest:$key", JSONObject()
                .put("name", suggestion.displayName)
                .put("countryCode", suggestion.countryCode ?: JSONObject.NULL)
                .put("countryName", suggestion.countryName ?: JSONObject.NULL)
                .put("city", suggestion.city ?: JSONObject.NULL)
                .put("zone", suggestion.timeZoneId ?: JSONObject.NULL)
                .put("timestamp", System.currentTimeMillis()).toString()).apply()
        }
        suggestion
    }

    /** Resolve only for a verified real physical fix; caller owns that safety gate. */
    suspend fun zoneForVerifiedPhysical(point: Coordinate): String? = withContext(Dispatchers.IO) {
        resolveZoneCached(point)
    }

    private fun resolveZoneCached(point: Coordinate): String? {
        val key = "zone:" + cacheKey(point)
        val cached = cache.getString(key, null)
        if (cached != null) {
            val item = runCatching { JSONObject(cached) }.getOrNull()
            if (item != null && System.currentTimeMillis() - item.optLong("timestamp") in 0..ttlMillis) {
                return optional(item, "zone")?.takeIf {
                    SavedPlaceTimePolicy.validatedZone(it) != null
                }
            }
        }
        val response = fetchJson(
            "https://timeapi.io/api/timezone/coordinate?latitude=" +
                "%.6f".format(Locale.US, point.latitude) + "&longitude=" +
                "%.6f".format(Locale.US, point.longitude)
        )
        val id = optional(response, "timeZone")
            ?.takeIf { SavedPlaceTimePolicy.validatedZone(it) != null }
        if (id != null) cache.edit().putString(key, JSONObject()
            .put("zone", id)
            .put("timestamp", System.currentTimeMillis()).toString()).apply()
        return id
    }

    private fun readCache(value: String): PlaceSuggestion? {
        val record = runCatching { JSONObject(value) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - record.optLong("timestamp") !in 0..ttlMillis)
            return null
        return PlaceSuggestion(
            displayName = optional(record, "name") ?: return null,
            countryCode = optional(record, "countryCode"),
            countryName = optional(record, "countryName"),
            city = optional(record, "city"),
            timeZoneId = optional(record, "zone")?.takeIf {
                SavedPlaceTimePolicy.validatedZone(it) != null
            },
        )
    }

    private fun parsePhoton(response: JSONObject?): List<GeocodeCandidate> {
        val features = response?.optJSONArray("features") ?: return emptyList()
        return (0 until minOf(features.length(), 12)).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val coordinates = feature.optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coordinates.length() < 2) return@mapNotNull null
            val point = runCatching {
                Coordinate(coordinates.getDouble(1), coordinates.getDouble(0))
            }.getOrNull() ?: return@mapNotNull null
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            GeocodeCandidate(
                name = optional(properties, "name"),
                coordinate = point,
                osmKey = optional(properties, "osm_key"),
                street = optional(properties, "street"),
                city = optional(properties, "city"),
                district = optional(properties, "district") ?: optional(properties, "locality"),
                countryCode = optional(properties, "countrycode"),
                countryName = optional(properties, "country"),
            )
        }
    }

    private fun optional(json: JSONObject?, name: String): String? =
        json?.optString(name)?.takeUnless { it.isBlank() || it.equals("null", true) }

    private fun cacheKey(point: Coordinate): String =
        "${(point.latitude * 10_000).roundToInt()}:${(point.longitude * 10_000).roundToInt()}"

    private fun fetchJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.connectTimeout = 3000
            connection.readTimeout = 4500
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PikminBee/1.0 (favourites geocoder)")
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    if (output.size() + n > 131_072) return null
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
