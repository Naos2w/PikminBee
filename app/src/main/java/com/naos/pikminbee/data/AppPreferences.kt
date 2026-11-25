package com.naos.pikminbee.data

import android.content.Context
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.ui.NaosThemeFamily
import com.naos.pikminbee.ui.NaosGlassMaterial
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.health.VirtualStridePolicy
import com.naos.pikminbee.domain.MockGpsJitterLevel
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

data class RouteDraftSnapshot(val stops: List<Coordinate>, val revision: Long)

/** One in-process draft state, observed by the phone UI, web API and planner. */
object RouteDraftState {
    val routeStops = MutableStateFlow<List<Coordinate>>(emptyList())
    private val mutableSnapshot = MutableStateFlow(RouteDraftSnapshot(emptyList(), 0))
    val routeSnapshot: kotlinx.coroutines.flow.StateFlow<RouteDraftSnapshot> = mutableSnapshot
    @Synchronized fun snapshot(): RouteDraftSnapshot = mutableSnapshot.value
    private val mutablePlanning = MutableStateFlow(false)
    val planning: kotlinx.coroutines.flow.StateFlow<Boolean> = mutablePlanning
    private var planningSequence = 0L
    private var planningOwner: Long? = null

    @Synchronized fun beginPlanning(revision: Long): Long? {
        if (planningOwner != null || mutableSnapshot.value.revision != revision) return null
        val owner = ++planningSequence
        planningOwner = owner
        mutablePlanning.value = true
        return owner
    }

    @Synchronized internal fun canCommit(expectedRevision: Long): Boolean =
        planningOwner == null && mutableSnapshot.value.revision == expectedRevision

    @Synchronized fun finishPlanning(owner: Long) {
        if (planningOwner != owner) return
        planningOwner = null
        mutablePlanning.value = false
    }

    /** All callers hold the same draft lock while persisting and publishing. */
    @Synchronized internal fun publish(points: List<Coordinate>) {
        val previous = mutableSnapshot.value
        if (previous.stops == points) return
        val immutable = points.toList()
        PreparedRouteState.route.value = null
        mutableSnapshot.value = RouteDraftSnapshot(immutable, previous.revision + 1)
        routeStops.value = immutable
    }
    val flowerStops = MutableStateFlow<List<Coordinate>>(emptyList())
    private var initialized = false

    @Synchronized fun initialize(route: List<Coordinate>, flowers: List<Coordinate>) {
        if (initialized) return
        publish(route)
        flowerStops.value = flowers
        initialized = true
    }
}

/** Current speed is temporary; the user's preferred default survives app restarts. */
object WalkSpeedState {
    val selected = MutableStateFlow(WalkSpeedPreset.DEFAULT)
    val preferred = MutableStateFlow(WalkSpeedPreset.DEFAULT)
    val preferredProfile = MutableStateFlow(WalkSpeedProfile.preset(WalkSpeedPreset.DEFAULT))
    val profile = MutableStateFlow(WalkSpeedProfile.preset(WalkSpeedPreset.DEFAULT))
    private var initialized = false

    fun resolvePreferred(savedDefault: WalkSpeedPreset, savedCustom: String?): WalkSpeedProfile =
        savedCustom?.let(WalkSpeedProfile::tryFixed)?.let {
            WalkSpeedProfile.fixed(it.fixedKmh, savedDefault)
        } ?: WalkSpeedProfile.preset(savedDefault)

    @Synchronized fun initialize(savedDefault: WalkSpeedPreset, savedCustom: String? = null) {
        if (!initialized) {
            val initial = resolvePreferred(savedDefault, savedCustom)
            selected.value = savedDefault
            preferred.value = savedDefault
            preferredProfile.value = initial
            profile.value = initial
            initialized = true
        }
    }

    fun update(next: WalkSpeedPreset) { updateProfile(WalkSpeedProfile.preset(next)) }
    fun updateProfile(next: WalkSpeedProfile) {
        profile.value = next
        if (next.mode == WalkSpeedMode.PRESET) selected.value = next.preset
    }
    fun updatePreferred(next: WalkSpeedPreset) = updatePreferredProfile(WalkSpeedProfile.preset(next))
    fun updatePreferredProfile(next: WalkSpeedProfile) {
        require(next.mode != WalkSpeedMode.RANGE)
        preferred.value = next.preset
        preferredProfile.value = next
    }
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

/** Shared lock for phone and paired Web edits to the same saved-place JSON blob. */
private object SavedPlacesLock

class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_preferences", Context.MODE_PRIVATE)

    init {
        RouteDraftState.initialize(loadPoints("route_draft"), loadPoints("flower_draft"))
        SavedRouteState.initialize(readSavedRoutes())
        WalkSpeedState.initialize(WalkSpeedPreset.fromSavedDefault(
            prefs.getString("walk_speed_default", null),
            prefs.getString("walk_speed_preset", null),
        ), prefs.getString("walk_speed_default_custom_kmh", null))
    }

    /** The first-launch wizard never requests permissions automatically. */
    fun setupCompleted(): Boolean = prefs.getBoolean("setup_completed", false)

    fun setSetupCompleted(done: Boolean) {
        prefs.edit().putBoolean("setup_completed", done).apply()
    }

    fun walkSpeedPreset(): WalkSpeedPreset = WalkSpeedState.selected.value
    fun defaultWalkSpeedPreset(): WalkSpeedPreset = WalkSpeedState.preferred.value
    fun walkSpeedProfile(): WalkSpeedProfile = WalkSpeedState.profile.value

    /** Selected advanced speeds belong to this session; preferred startup preset is unchanged. */
    fun setWalkSpeedProfile(next: WalkSpeedProfile) {
        WalkSpeedState.updateProfile(next)
        when (next.mode) {
            WalkSpeedMode.FIXED -> prefs.edit()
                .putFloat("speed_custom_kmh", next.fixedKmh.toFloat()).apply()
            WalkSpeedMode.RANGE -> prefs.edit()
                .putFloat("speed_min_kmh", next.minKmh.toFloat())
                .putFloat("speed_max_kmh", next.maxKmh.toFloat()).apply()
            WalkSpeedMode.PRESET -> Unit
        }
    }

    fun lastCustomSpeedKmh(): Double = prefs.getFloat("speed_custom_kmh", 12f)
        .toDouble().takeIf { it.isFinite() && it in WalkSpeedProfile.MIN_KMH..WalkSpeedProfile.MAX_KMH }
        ?: 12.0
    fun lastRangeMinKmh(): Double = prefs.getFloat("speed_min_kmh", 10.8f).toDouble()
    fun lastRangeMaxKmh(): Double = prefs.getFloat("speed_max_kmh", 15f).toDouble()

    /** Virtual distance/stride setting applies at the start of the next route. */
    fun virtualStrideMetres(): Double {
        val text = runCatching { prefs.getString("virtual_stride_metres", null) }
            .getOrNull()?.toDoubleOrNull()
        val legacyFloat = if (text == null)
            runCatching { prefs.getFloat("virtual_stride_metres", Float.NaN).toDouble() }.getOrNull()
        else null
        return VirtualStridePolicy.fromSaved(text ?: legacyFloat)
    }
    fun setVirtualStrideMetres(value: Double) {
        require(VirtualStridePolicy.valid(value))
        prefs.edit().putString("virtual_stride_metres", value.toString()).apply()
    }

    /** Nonphysical mock-GPS drift is opt-in; old installs remain exact by default. */
    fun mockGpsJitterLevel(): MockGpsJitterLevel =
        MockGpsJitterLevel.fromSaved(prefs.getString("mock_gps_jitter_level", null))

    fun setMockGpsJitterLevel(level: MockGpsJitterLevel) {
        prefs.edit().putString("mock_gps_jitter_level", level.name).apply()
    }

    /** Changes this session's speed; does not change the user's saved startup default. */
    fun setWalkSpeedPreset(next: WalkSpeedPreset) {
        setWalkSpeedProfile(WalkSpeedProfile.preset(next))
    }

    /** Explicit user choice; only this changes the next App launch's default speed. */
    fun setDefaultWalkSpeedPreset(next: WalkSpeedPreset) =
        setDefaultWalkSpeedProfile(WalkSpeedProfile.preset(next))

    /** The default is independent of an active test session. Preserve legacy preset as fallback. */
    fun setDefaultWalkSpeedProfile(next: WalkSpeedProfile) {
        require(next.mode != WalkSpeedMode.RANGE)
        prefs.edit().putString("walk_speed_default", next.preset.name).apply {
            if (next.mode == WalkSpeedMode.FIXED)
                putString("walk_speed_default_custom_kmh", next.fixedKmh.toString())
            else remove("walk_speed_default_custom_kmh")
        }.apply()
        WalkSpeedState.updatePreferredProfile(next)
    }

    fun provider(): MapProvider = MapBasemapPolicy.resolveProvider(
        enumValues<MapProvider>().firstOrNull { it.name == prefs.getString("provider", "") }
            ?: MapProvider.OPEN_FREE_MAP,
        BuildConfig.HAS_GOOGLE_MAPS_KEY,
    )

    fun appearance(): MapAppearance =
        MapAppearancePolicy.fromStored(prefs.getString("appearance", null))

    fun freeMapStyle(): FreeMapStyle =
        MapBasemapPolicy.fromStored(prefs.getString("free_map_style", ""))

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
    ) = synchronized(SavedPlacesLock) {
        val list = places()
        // Ensure IDs cannot collide when paired Web and phone save in the same millisecond.
        var id = System.currentTimeMillis()
        while (list.any { it.id == id }) id++
        val saved = SavedPlace(
            id, name.trim().take(80).ifBlank { "收藏地點" }, coordinate,
            countryCode?.takeIf { it.length == 2 }?.uppercase(java.util.Locale.ROOT),
            countryName?.take(100), city?.take(100),
            timeZoneId?.takeIf { SavedPlaceTimePolicy.validatedZone(it) != null },
        )
        savePlaces(list + saved)
    }

    fun removePlace(id: Long) = synchronized(SavedPlacesLock) {
        savePlaces(places().filterNot { it.id == id })
    }

    /** Manual names are user-owned; renaming never invokes geocoding or alters metadata. */
    fun renamePlace(id: Long, name: String): Boolean = synchronized(SavedPlacesLock) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > 80) return@synchronized false
        val items = places()
        if (items.none { it.id == id }) return@synchronized false
        savePlaces(items.map { if (it.id == id) it.copy(name = trimmed) else it })
        true
    }

    /** A late network reply may update only metadata for exactly the original saved ID/point.
     *  It must not resurrect a deleted place or overwrite a renamed place. */
    fun enrichSavedPlace(id: Long, expected: Coordinate, details: PlaceSuggestion): Boolean =
        synchronized(SavedPlacesLock) {
            val existing = places()
            val matching = existing.firstOrNull { it.id == id && it.coordinate == expected }
                ?: return@synchronized false
            val enriched = SavedPlaceMetadataPolicy.enriched(matching, details)
            if (enriched == matching) return@synchronized false
            savePlaces(existing.map { if (it.id == id) enriched else it })
            true
        }

    /** Route and flower waypoint drafts are separate from saved places. */
    fun routeDraft(): List<Coordinate> = RouteDraftState.routeStops.value
    fun flowerDraft(): List<Coordinate> = RouteDraftState.flowerStops.value
    fun saveRouteDraft(points: List<Coordinate>) {
        synchronized(RouteDraftState) {
            if (RouteDraftState.snapshot().stops != points) {
                savePoints("route_draft", points)
                RouteDraftState.publish(points)
            }
        }
    }

    /** Compare and commit under the same lock; reject stale phone or browser edits. */
    fun saveRouteDraftIfCurrent(expectedRevision: Long, points: List<Coordinate>): Boolean =
        synchronized(RouteDraftState) {
            if (!RouteDraftState.canCommit(expectedRevision)) return@synchronized false
            saveRouteDraft(points)
            true
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
