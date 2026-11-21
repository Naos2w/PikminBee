package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.ui.NaosThemeFamily
import com.naos.pikminbee.ui.NaosGlassMaterial
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkSpeedPreset
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow

enum class MapProvider { OPEN_FREE_MAP, GOOGLE_MAPS }
enum class MapAppearance { SYSTEM, LIGHT, DARK }

/** The OS choice is the default; users can still lock light or dark manually. */
object MapAppearancePolicy {
    fun fromStored(value: String?): MapAppearance =
        MapAppearance.entries.firstOrNull { it.name == value } ?: MapAppearance.SYSTEM

    fun isDark(mode: MapAppearance, systemDark: Boolean): Boolean = when (mode) {
        MapAppearance.SYSTEM -> systemDark
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
}

/** Local saved coordinate with optional resolved metadata. Null means unknown, never inferred. */
data class SavedPlace(
    val id: Long,
    val name: String,
    val coordinate: Coordinate,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val timeZoneId: String? = null,
    val createdAtMillis: Long = id,
)

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

/** Current speed is temporary; the user's preferred default survives app restarts. */
object WalkSpeedState {
    val selected = MutableStateFlow(WalkSpeedPreset.DEFAULT)
    val preferred = MutableStateFlow(WalkSpeedPreset.DEFAULT)
    private var initialized = false

    @Synchronized fun initialize(savedDefault: WalkSpeedPreset) {
        if (!initialized) {
            selected.value = savedDefault
            preferred.value = savedDefault
            initialized = true
        }
    }

    fun update(next: WalkSpeedPreset) { selected.value = next }
    fun updatePreferred(next: WalkSpeedPreset) { preferred.value = next }
}

/** Saved routes are a distinct library from the currently edited map draft. */
object SavedRouteState {
    val routes = MutableStateFlow<List<SavedRoute>>(emptyList())
    private var initialized = false

    @Synchronized fun initialize(items: List<SavedRoute>) {
        if (initialized) return
        routes.value = items
        initialized = true
    }
}

class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_preferences", Context.MODE_PRIVATE)

    init {
        RouteDraftState.initialize(loadPoints("route_draft"), loadPoints("flower_draft"))
        SavedRouteState.initialize(readSavedRoutes())
        WalkSpeedState.initialize(WalkSpeedPreset.fromSavedDefault(
            prefs.getString("walk_speed_default", null),
            prefs.getString("walk_speed_preset", null),
        ))
    }

    /** The first-launch wizard never requests permissions automatically. */
    fun setupCompleted(): Boolean = prefs.getBoolean("setup_completed", false)

    fun setSetupCompleted(done: Boolean) {
        prefs.edit().putBoolean("setup_completed", done).apply()
    }

    fun walkSpeedPreset(): WalkSpeedPreset = WalkSpeedState.selected.value
    fun defaultWalkSpeedPreset(): WalkSpeedPreset = WalkSpeedState.preferred.value

    /** Changes this session's speed; does not change the user's saved startup default. */
    fun setWalkSpeedPreset(next: WalkSpeedPreset) {
        WalkSpeedState.update(next)
    }

    /** Explicit user choice; only this changes the next App launch's default speed. */
    fun setDefaultWalkSpeedPreset(next: WalkSpeedPreset) {
        prefs.edit().putString("walk_speed_default", next.name).apply()
        WalkSpeedState.updatePreferred(next)
    }

    fun provider(): MapProvider = MapBasemapPolicy.resolveProvider(
        enumValues<MapProvider>().firstOrNull { it.name == prefs.getString("provider", "") }
            ?: MapProvider.OPEN_FREE_MAP,
        BuildConfig.HAS_GOOGLE_MAPS_KEY,
    )

    fun appearance(): MapAppearance =
        MapAppearancePolicy.fromStored(prefs.getString("appearance", null))

    fun freeMapStyle(): FreeMapStyle =
        enumValues<FreeMapStyle>().firstOrNull { it.name == prefs.getString("free_map_style", "") }
            ?: FreeMapStyle.AUTO

    fun setFreeMapStyle(style: FreeMapStyle) {
        prefs.edit().putString("free_map_style", style.name).apply()
    }

    fun setProvider(provider: MapProvider) {
        val resolved = MapBasemapPolicy.resolveProvider(provider, BuildConfig.HAS_GOOGLE_MAPS_KEY)
        prefs.edit().putString("provider", resolved.name).apply()
    }
    fun setAppearance(appearance: MapAppearance) { prefs.edit().putString("appearance", appearance.name).apply() }

    /** One product identity; old theme preferences are intentionally ignored. */
    fun naosThemeFamily(): NaosThemeFamily = NaosThemeFamily.MINERAL
    fun naosGlassMaterial(): NaosGlassMaterial = NaosGlassMaterial.REGULAR

    /** The last virtual fix is opt-in recall only, never automatic mock GPS restart. */
    fun rememberVirtualPosition(point: Coordinate) {
        prefs.edit().putString("last_virtual_for_overlay",
            "${point.latitude}|${point.longitude}|${System.currentTimeMillis()}").apply()
    }

    fun lastVirtualPosition(nowMillis: Long = System.currentTimeMillis()): Coordinate? =
        runCatching {
            val parts = prefs.getString("last_virtual_for_overlay", null)
                ?.split('|') ?: return@runCatching null
            if (parts.size != 3) return@runCatching null
            val savedAt = parts[2].toLong()
            if (nowMillis - savedAt !in 0..(12 * 60 * 60 * 1000L)) return@runCatching null
            Coordinate(parts[0].toDouble(), parts[1].toDouble())
        }.getOrNull()

    fun overlayFirstUseGuideEnabled(): Boolean =
        prefs.getBoolean("overlay_first_guide_enabled", true)

    fun setOverlayFirstUseGuideEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_first_guide_enabled", enabled).apply()
    }

    fun overlayGuideSeen(): Boolean = prefs.getBoolean("overlay_guide_seen", false)

    fun markOverlayGuideSeen() { prefs.edit().putBoolean("overlay_guide_seen", true).apply() }

    fun overlayLongPressTips(): Boolean =
        prefs.getBoolean("overlay_long_press_tips", true)

    fun setOverlayLongPressTips(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_long_press_tips", enabled).apply()
    }

    fun places(): List<SavedPlace> = runCatching {
        val json = JSONArray(prefs.getString("places", "[]") ?: "[]")
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            val coordinate = runCatching { Coordinate(lat, lon) }.getOrNull()
                ?: return@mapNotNull null
            val id = item.optLong("id")
            fun optional(key: String): String? =
                item.optString(key).takeUnless { it.isBlank() || it.equals("null", true) }
            SavedPlace(
                id, item.optString("name"), coordinate,
                countryCode = optional("countryCode"),
                countryName = optional("countryName"),
                city = optional("city"),
                timeZoneId = optional("timeZoneId"),
                createdAtMillis = item.optLong("createdAt", id),
            )
        }
    }.getOrDefault(emptyList())

    fun addPlace(
        name: String, coordinate: Coordinate,
        countryCode: String? = null, countryName: String? = null,
        city: String? = null, timeZoneId: String? = null,
    ) {
        val list = places()
        val id = (System.currentTimeMillis()).let { now ->
            if (list.any { it.id == now }) now + 1 else now
        }
        val saved = SavedPlace(
            id, name.trim().take(60).ifBlank { "收藏地點" }, coordinate,
            countryCode?.takeIf { it.length == 2 }?.uppercase(java.util.Locale.ROOT),
            countryName?.take(100), city?.take(100), timeZoneId?.take(100),
        )
        savePlaces(list + saved)
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

    fun savedRoutes(): List<SavedRoute> = SavedRouteState.routes.value

    fun addSavedRoute(name: String, mode: WalkMode, stops: List<Coordinate>): SavedRoute =
        synchronized(SavedRouteState) {
            val current = SavedRouteState.routes.value
            require(current.size < 100) { "最多可收藏 100 條路線，請先刪除不需要的路線" }
            val route = SavedRoute.create(name, mode, stops)
            val next = listOf(route) + current
            persistSavedRoutes(next)
            SavedRouteState.routes.value = next
            route
        }

    fun deleteSavedRoute(id: String): Boolean = synchronized(SavedRouteState) {
        val current = SavedRouteState.routes.value
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return@synchronized false
        persistSavedRoutes(next)
        SavedRouteState.routes.value = next
        true
    }

    fun renameSavedRoute(id: String, newName: String): Boolean = synchronized(SavedRouteState) {
        val current = SavedRouteState.routes.value
        val old = current.firstOrNull { it.id == id } ?: return@synchronized false
        val name = newName.trim()
        require(name.isNotEmpty() && name.length <= 60) { "路線名稱需為 1 至 60 字" }
        val next = current.map { if (it.id == old.id) it.copy(name = name) else it }
        persistSavedRoutes(next)
        SavedRouteState.routes.value = next
        true
    }

    fun loadSavedRouteIntoDraft(route: SavedRoute) {
        if (route.mode == WalkMode.FLOWERS) saveFlowerDraft(route.stops)
        else saveRouteDraft(route.stops)
    }

    private fun readSavedRoutes(): List<SavedRoute> = runCatching {
        val json = JSONArray(prefs.getString("saved_routes", "[]") ?: "[]")
        if (json.length() > 100) return@runCatching emptyList<SavedRoute>()
        (0 until json.length()).mapNotNull { index ->
            runCatching {
                val entry = json.getJSONObject(index)
                val points = entry.getJSONArray("points")
                require(points.length() in 1..15)
                val stops = (0 until points.length()).map { i ->
                    val item = points.getJSONObject(i)
                    Coordinate(item.getDouble("lat"), item.getDouble("lon"))
                }
                SavedRoute(
                    id = entry.getString("id"),
                    name = entry.getString("name"),
                    mode = WalkMode.valueOf(entry.getString("mode")),
                    stops = stops,
                    createdAtMillis = entry.optLong("createdAt", 0L),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun persistSavedRoutes(routes: List<SavedRoute>) {
        val records = JSONArray().apply {
            routes.forEach { route ->
                val points = JSONArray().apply {
                    route.stops.forEach { point ->
                        put(JSONObject().put("lat", point.latitude).put("lon", point.longitude))
                    }
                }
                put(JSONObject()
                    .put("id", route.id)
                    .put("name", route.name)
                    .put("mode", route.mode.name)
                    .put("createdAt", route.createdAtMillis)
                    .put("points", points))
            }
        }
        check(prefs.edit().putString("saved_routes", records.toString()).commit()) {
            "無法寫入收藏路線，資料未儲存"
        }
    }

    private fun savePlaces(places: List<SavedPlace>) {
        val values = JSONArray().apply {
            places.forEach { place ->
                put(JSONObject().put("id", place.id).put("name", place.name)
                    .put("lat", place.coordinate.latitude).put("lon", place.coordinate.longitude)
                    .put("countryCode", place.countryCode ?: JSONObject.NULL)
                    .put("countryName", place.countryName ?: JSONObject.NULL)
                    .put("city", place.city ?: JSONObject.NULL)
                    .put("timeZoneId", place.timeZoneId ?: JSONObject.NULL)
                    .put("createdAt", place.createdAtMillis))
            }
        }
        prefs.edit().putString("places", values.toString()).apply()
    }
}
