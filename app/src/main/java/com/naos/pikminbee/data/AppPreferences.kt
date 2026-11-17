package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.domain.Coordinate
import org.json.JSONArray
import org.json.JSONObject

enum class MapProvider { OPEN_FREE_MAP, GOOGLE_MAPS }
enum class MapAppearance { SYSTEM, LIGHT, DARK }

data class SavedPlace(val id: Long, val name: String, val coordinate: Coordinate)

class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_preferences", Context.MODE_PRIVATE)

    fun provider(): MapProvider =
        enumValues<MapProvider>().firstOrNull { it.name == prefs.getString("provider", "") }
            ?: MapProvider.OPEN_FREE_MAP

    fun appearance(): MapAppearance =
        enumValues<MapAppearance>().firstOrNull { it.name == prefs.getString("appearance", "") }
            ?: MapAppearance.SYSTEM

    fun setProvider(provider: MapProvider) { prefs.edit().putString("provider", provider.name).apply() }
    fun setAppearance(appearance: MapAppearance) { prefs.edit().putString("appearance", appearance.name).apply() }

    fun places(): List<SavedPlace> = runCatching {
        val json = JSONArray(prefs.getString("places", "[]") ?: "[]")
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            val coordinate = runCatching { Coordinate(lat, lon) }.getOrNull()
                ?: return@mapNotNull null
            SavedPlace(item.optLong("id"), item.optString("name"), coordinate)
        }
    }.getOrDefault(emptyList())

    fun addPlace(name: String, coordinate: Coordinate) {
        val list = places()
        val id = System.currentTimeMillis()
        savePlaces(list + SavedPlace(id, name.trim().take(60).ifBlank { "收藏地點" }, coordinate))
    }

    fun removePlace(id: Long) { savePlaces(places().filterNot { it.id == id }) }

    private fun savePlaces(places: List<SavedPlace>) {
        val values = JSONArray().apply {
            places.forEach { place ->
                put(JSONObject().put("id", place.id).put("name", place.name)
                    .put("lat", place.coordinate.latitude).put("lon", place.coordinate.longitude))
            }
        }
        prefs.edit().putString("places", values.toString()).apply()
    }
}
