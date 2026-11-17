package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.domain.Coordinate
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow

enum class MapProvider { OPEN_FREE_MAP, GOOGLE_MAPS }
enum class MapAppearance { SYSTEM, LIGHT, DARK }

data class SavedPlace(val id: Long, val name: String, val coordinate: Coordinate)

/** One in-process draft state, observed by the phone UI, web API and GPS planner. */
object RouteDraftState {
    val routeStops = MutableStateFlow<List<Coordinate>>(emptyList())
    val flowerStops = MutableStateFlow<List<Coordinate>>(emptyList())
    private var initialized = false

    @Synchronized fun initialize(route: List<Coordinate>, flowers: List<Coordinate>) {
        if (initialized) return
        routeStops.value = route
        flowerStops.value = flowers
        initialized = true
    }
}

class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_preferences", Context.MODE_PRIVATE)

    init {
        RouteDraftState.initialize(loadPoints("route_draft"), loadPoints("flower_draft"))
    }

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

    /** Route and flower waypoint drafts are separate from saved places. */
    fun routeDraft(): List<Coordinate> = RouteDraftState.routeStops.value
    fun flowerDraft(): List<Coordinate> = RouteDraftState.flowerStops.value
    fun saveRouteDraft(points: List<Coordinate>) {
        synchronized(RouteDraftState) {
            savePoints("route_draft", points)
            RouteDraftState.routeStops.value = points
        }
    }
    fun saveFlowerDraft(points: List<Coordinate>) {
        synchronized(RouteDraftState) {
            savePoints("flower_draft", points)
            RouteDraftState.flowerStops.value = points
        }
    }

    private fun loadPoints(key: String): List<Coordinate> = runCatching {
        val json = JSONArray(prefs.getString(key, "[]") ?: "[]")
        if (json.length() > 15) return@runCatching emptyList()
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            runCatching { Coordinate(item.getDouble("lat"), item.getDouble("lon")) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun savePoints(key: String, points: List<Coordinate>) {
        require(points.size <= 15)
        val json = JSONArray()
        points.forEach { json.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
        prefs.edit().putString(key, json.toString()).apply()
    }

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
