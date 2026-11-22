package com.naos.pikminbee.lan

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationCompat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.naos.pikminbee.ui.BeePermissions
import com.naos.pikminbee.ui.VerifiedPhysicalFixState
import com.naos.pikminbee.data.PreparedRouteState
import com.naos.pikminbee.domain.PreparedWalkingRoute
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.domain.WalkPhase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.PlaceLookup
import com.naos.pikminbee.data.PlaceSuggestion
import com.naos.pikminbee.data.PlaceSuggestionPolicy
import com.naos.pikminbee.data.SavedPlaceMetadataPolicy
import com.naos.pikminbee.data.VerifiedRealTimeZone
import com.naos.pikminbee.flower.FlowerCatalogStore
import com.naos.pikminbee.flower.FlowerEvidence
import com.naos.pikminbee.flower.FlowerFinder
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.route.ValhallaPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class LanDisplay(
    val active: Boolean = false,
    val address: String = "",
    val pairingPin: String = "",
    val pendingDevice: String = "",
    val note: String = "Wi-Fi 控制已關閉",
)

object LanStatus {
    val display = MutableStateFlow(LanDisplay())
}

object LanWifiAddress {
    fun resolve(context: Context): Inet4Address? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val links = cm.getLinkProperties(network) ?: continue
            for (entry in links.linkAddresses) {
                val ip = entry.address as? Inet4Address ?: continue
                if (ip.isSiteLocalAddress) return ip
            }
        }
        return null
    }
}

/**
 * Opt-in LAN access bound to the current private Wi-Fi address.
 * Commands go into the existing foreground location service; there is no extra GPS engine.
 */
class LanController(private val context: Context) {
    private val prefs = AppPreferences(context)
    private val flowers = FlowerCatalogStore(context)
    private val placeLookup = PlaceLookup(context)
    private val pairing = LanPairing()
    private val rateLimiter = LanRateLimiter()
    private val planning = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private data class WebPreview(val id: String, val route: PreparedWalkingRoute)
    private val previews = ConcurrentHashMap<WalkMode, WebPreview>()
    private data class PendingPlaceLookup(
        val coordinate: Coordinate, val requestedAtMillis: Long,
        @Volatile var suggestion: PlaceSuggestion? = null,
        @Volatile var completed: Boolean = false,
    )
    private val placeLookups = ConcurrentHashMap<String, PendingPlaceLookup>()
    private val placeEnrichmentBusy = AtomicBoolean(false)
    @Volatile private var placeEnrichmentNote = ""
    @Volatile private var browserOrigin: Coordinate? = null
    @Volatile private var usedPreviewId: String? = null
    // Real phone location is only sampled after an explicitly approved browser action.
    // Never cache or publish a test-provider fix as a real physical position.
    private val realRequestGeneration = AtomicInteger()
    @Volatile private var realRequestedAtMillis = 0L
    @Volatile private var realFix: Coordinate? = null
    @Volatile private var realFixTimestampMillis = 0L
    @Volatile private var realFixAccuracyMetres = -1f
    @Volatile private var realGpsState = "idle"
    @Volatile private var verifiedTimeZoneReference: VerifiedRealTimeZone? = null
    private val realZoneLookupBusy = AtomicBoolean(false)
    @Volatile private var lastRealZoneLookupAtMillis = 0L
    @Volatile private var lastObservedMock = false
    private fun origin(): Coordinate? = browserOrigin ?: LocationStatus.selected.value
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: LanHttpServer? = null
    private var bound: Inet4Address? = null
    private var lastNetworkCheck = 0L

    fun start(): String {
        if (server != null) return "已啟用；請在手機查看網址與配對碼"
        val ip = LanWifiAddress.resolve(context)
            ?: return "請先連接私人 Wi-Fi；不會透過行動網路或公網啟用服務"
        pairing.rotate()
        val next = LanHttpServer(ip, handler = ::dispatch)
        return runCatching {
            next.start()
            bound = ip
            server = next
            LanStatus.display.value = LanDisplay(
                active = true, address = "http://${ip.hostAddress}:8765/",
                pairingPin = pairing.currentPin(),
                note = "僅接受可互通的私人區網，配對碼不會上傳雲端",
            )
            "已開啟電腦網頁控制"
        }.getOrElse {
            pairing.revoke()
            LanStatus.display.value = LanDisplay(note = "無法開啟：${it.message}")
            "無法啟動 Wi-Fi 服務：${it.message}"
        }
    }

    fun approvePending() {
        val approved = pairing.approve()
        LanStatus.display.value = LanStatus.display.value.copy(
            pendingDevice = "",
            note = if (approved) "已核准網頁裝置，等待瀏覽器建立工作階段" else "沒有待核准的裝置")
    }

    fun denyPending() {
        pairing.deny()
        LanStatus.display.value = LanStatus.display.value.copy(
            pendingDevice = "", note = "已拒絕裝置配對")
    }

    fun stop(reason: String = "Wi-Fi 控制已關閉") {
        realRequestGeneration.incrementAndGet()
        realFix = null
        verifiedTimeZoneReference = null
        realGpsState = "idle"
        previews.clear()
        placeLookups.clear()
        placeEnrichmentNote = ""
        browserOrigin = null
        usedPreviewId = null
        server?.stop()
        server = null
        bound = null
        pairing.revoke()
        LanStatus.display.value = LanDisplay(note = reason)
    }

    fun checkNetwork() {
        if (server == null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNetworkCheck < 10_000L) return
        lastNetworkCheck = now
        if (LanWifiAddress.resolve(context) != bound) {
            stop("Wi-Fi 已切換或斷線，網頁控制自動關閉")
        }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun dispatch(req: LanRequest): LanReply {
        if (req.path.startsWith("/api/") && !rateLimiter.allow(req.remoteAddress)) {
            return error(429, "操作過於頻繁，請稍後再試")
        }
        if (req.method == "GET" && req.path == "/") {
            return asset("lan/index.html", "text/html; charset=utf-8")
        }
        if (req.method == "GET" && req.path == "/controller.js") {
            return asset("lan/controller.js", "application/javascript; charset=utf-8")
        }
        if (req.method == "GET" && req.path == "/vendor/maplibre-gl.mjs") {
            return asset("lan/vendor/maplibre-gl/maplibre-gl.mjs", "application/javascript; charset=utf-8")
        }
        // MapLibre GL 6.x uses a dedicated ES-module worker for vector tiles.
        // Serve the exact same-version local worker, never an external CDN URL.
        if (req.method == "GET" && req.path == "/vendor/maplibre-gl-worker.mjs") {
            return asset("lan/vendor/maplibre-gl/maplibre-gl-worker.mjs",
                "application/javascript; charset=utf-8")
        }
        if (req.method == "GET" && req.path == "/vendor/maplibre-gl.css") {
            return asset("lan/vendor/maplibre-gl/maplibre-gl.css", "text/css; charset=utf-8")
        }
        if (req.method == "POST" && req.path == "/api/pair") {
            val json = parse(req.body) ?: return error(400, "JSON 無效")
            val pin = json.optString("pin")
            if (!pin.matches(Regex("[0-9]{6}"))) return error(400, "請輸入六位數配對碼")
            val result = pairing.pair(req.remoteAddress, pin, json.optString("deviceName"))
            if (result.locked) return error(423, "配對嘗試過多或已有待核准裝置")
            if (result.pendingId == null) return error(401, "配對碼錯誤")
            val pending = pairing.pendingDevice()
            LanStatus.display.value = LanStatus.display.value.copy(
                pendingDevice = pending?.let { "${it.name} · ${it.address}" }.orEmpty(),
                note = "新裝置要求連線，請在手機上核准或拒絕")
            return acceptedJson(JSONObject().put("pendingId", result.pendingId).toString())
        }
        if (req.method == "GET" && req.path == "/api/pair/status") {
            val status = pairing.poll(req.remoteAddress, req.pendingId)
            return when (status.state) {
                "approved" -> ok(JSONObject().put("token", status.token)
                    .put("expiresInSeconds", 1800).toString())
                "pending" -> acceptedJson(JSONObject().put("status", "pending").toString())
                else -> error(403, "裝置尚未核准、已拒絕或配對已過期")
            }
        }
        if (!pairing.authorised(req.token, req.remoteAddress)) return error(401, "請在手機取得配對碼後重新連線")
        if (req.method == "GET" && req.path == "/api/state") return ok(snapshot())
        if (req.method == "GET" && req.path == "/api/saved") return ok(savedSnapshot())
        if (req.method != "POST") return error(404, "找不到操作")
        return when (req.path) {
            "/api/teleport" -> {
                if (walking()) return error(409, "路線進行中；請先按停止，再確認瞬移")
                if (!mockPermissionReady()) return error(409, "請先在手機完成精確定位及模擬位置 App 設定")
                val target = coordinate(req.body) ?: return error(400, "經緯度無效")
                runCatching { ContextCompat.startForegroundService(context,
                    MockLocationService.teleportIntent(context, target)) }
                    .fold({ accepted("已送出瞬移指令") },
                        { error(409, "無法瞬移：" + (it.message ?: "請確認手機狀態")) })
            }
            "/api/flowers/nearby" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val candidate = coordinate(json) ?: return error(400, "請選擇有效搜尋中心")
                val radius = json.optDouble("radiusMetres", 2_000.0)
                if (radius !in listOf(500.0, 2_000.0, 5_000.0))
                    return error(400, "搜尋範圍限 500 公尺、2 公里或 5 公里")
                val onlyChecked = json.optBoolean("onlyChecked", true)
                val matches = FlowerFinder.nearby(
                    candidate, flowers.all(), radius, onlyChecked, maxCount = 100)
                val rows = JSONArray().apply {
                    matches.forEach { spot ->
                        put(JSONObject()
                            .put("id", spot.id).put("name", spot.name)
                            .put("lat", spot.coordinate.latitude)
                            .put("lon", spot.coordinate.longitude)
                            .put("distanceMetres",
                                WalkingGeometry.distanceMeters(candidate, spot.coordinate))
                            .put("evidence", spot.evidence.name))
                    }
                }
                ok(JSONObject().put("flowers", rows)
                    .put("source", "USER_CATALOGUE_NOT_GAME_API").toString())
            }
            "/api/flowers/optimise" -> {
                if (walking()) return error(409, "行走中無法最佳化尋花順序")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val loop = json.optBoolean("loop", true)
                val start = origin() ?: return error(409, "請先指定尋花起點")
                val stops = points(WalkMode.FLOWERS).toList()
                if (stops.size < if (loop) 2 else 3)
                    return error(409, "花點太少，維持目前順序即可")
                if (stops.size > 14) return error(409, "最多 14 個尋花點")
                if (!planning.compareAndSet(false, true))
                    return error(409, "仍有道路規劃進行中")
                LanStatus.display.value = LanStatus.display.value.copy(
                    note = "正在向 Valhalla 請求步行道路順序，不會移動 GPS")
                scope.launch {
                    try {
                        val updated = ValhallaPlanner.optimiseFlowerStops(start, stops, loop)
                        if (origin() != start || points(WalkMode.FLOWERS) != stops) {
                            LanStatus.display.value = LanStatus.display.value.copy(
                                note = "花點或起點已變更，已保留目前順序")
                        } else if (updated != stops) {
                            savePoints(WalkMode.FLOWERS, updated)
                            invalidate(WalkMode.FLOWERS)
                            LanStatus.display.value = LanStatus.display.value.copy(
                                note = "已套用步行道路建議順序，請重新預覽再開始")
                        } else {
                            LanStatus.display.value = LanStatus.display.value.copy(
                                note = "目前順序已符合道路建議，未更動草稿")
                        }
                    } catch (e: Exception) {
                        LanStatus.display.value = LanStatus.display.value.copy(
                            note = "道路順序建議失敗，保留原順序：" +
                                (e.message ?: "請稍後再試"))
                    } finally { planning.set(false) }
                }
                accepted("正在規劃道路順序，完成前不能開始行走")
            }
            "/api/flowers/add" -> {
                if (walking() || planning.get())
                    return error(409, "行走或規劃中無法修改尋花路點")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val id = json.optString("id")
                val spot = flowers.all().firstOrNull { it.id == id }
                    ?: return error(404, "花點不存在或已移除")
                if (spot.evidence == FlowerEvidence.UNVERIFIED_IMPORT &&
                    !json.optBoolean("confirmUnverified", false))
                    return error(409, "匯入候選尚未確認，請明確同意後再加入")
                val result = LanRouteDraftPolicy.add(
                    WalkMode.FLOWERS, points(WalkMode.FLOWERS),
                    spot.coordinate, origin())
                if (!result.accepted) return error(409, result.message)
                savePoints(WalkMode.FLOWERS, result.stops)
                invalidate(WalkMode.FLOWERS)
                accepted("已加入「${spot.name}」；請先預覽步行道路")
            }
            "/api/points/add" -> {
                if (walking()) return error(409, "行走中無法修改路點，請先停止")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val target = coordinate(json) ?: return error(400, "經緯度無效")
                val result = LanRouteDraftPolicy.add(
                    mode, points(mode), target, origin(),
                )
                if (!result.accepted) return error(409, result.message)
                savePoints(mode, result.stops)
                invalidate(mode)
                accepted(result.message)
            }
            "/api/points/reorder", "/api/points/remove" -> {
                if (walking()) return error(409, "行走中無法修改路點")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val index = json.optInt("index", -1)
                val current = points(mode)
                val result = if (req.path.endsWith("reorder"))
                    LanRouteDraftPolicy.reorder(current, index, json.optInt("offset", 0))
                else LanRouteDraftPolicy.remove(current, index)
                if (!result.accepted) return error(409, result.message)
                savePoints(mode, result.stops)
                invalidate(mode)
                accepted(result.message)
            }
            "/api/points/undo", "/api/points/clear" -> {
                if (walking()) return error(409, "行走中無法修改路點，請先停止")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val current = points(mode)
                savePoints(mode, if (req.path.endsWith("undo")) current.dropLast(1) else emptyList())
                invalidate(mode)
                accepted("路點已更新")
            }
            "/api/speed" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val preset = WalkSpeedPreset.entries.firstOrNull {
                    it.name == json.optString("speedPreset")
                } ?: return error(400, "不支援的速度預設")
                prefs.setWalkSpeedPreset(preset)
                if (walking()) {
                    context.startService(MockLocationService.speedIntent(context, preset))
                }
                accepted("本次速度已設定：${preset.display}")
            }
            "/api/speed/default" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val preset = WalkSpeedPreset.entries.firstOrNull {
                    it.name == json.optString("speedPreset")
                } ?: return error(400, "不支援的預設速度")
                prefs.setDefaultWalkSpeedPreset(preset)
                accepted("下次開啟 App 的預設速度已設為：${preset.display}")
            }
            "/api/location/real" -> requestRealLocation()
            "/api/origin" -> {
                if (walking()) return error(409, "行走中無法修改起點；請先停止")
                val point = coordinate(req.body) ?: return error(400, "起點經緯度無效")
                browserOrigin = point
                invalidate(WalkMode.ROUTE)
                invalidate(WalkMode.FLOWERS)
                accepted("已設定路線起點；尚未移動 GPS")
            }
            "/api/saved/place/lookup" -> {
                // The browser must have explicitly selected a coordinate before requesting
                // a third-party Photon/TimeAPI lookup. Nothing runs on GPS updates.
                val point = coordinate(req.body) ?: return error(400, "收藏座標無效")
                val now = System.currentTimeMillis()
                placeLookups.entries.removeIf { now - it.value.requestedAtMillis > 120_000L }
                if (placeLookups.size >= 8) return error(429, "收藏查詢過多，請稍後再試")
                val requestId = UUID.randomUUID().toString()
                val lookup = PendingPlaceLookup(point, now)
                placeLookups[requestId] = lookup
                scope.launch {
                    try {
                        lookup.suggestion = placeLookup.suggest(point)
                    } catch (e: Exception) {
                        lookup.suggestion = PlaceSuggestion(
                            PlaceSuggestionPolicy.pointLabel(point))
                    } finally {
                        lookup.completed = true
                    }
                }
                acceptedJson(JSONObject().put("lookupId", requestId).toString())
            }
            "/api/saved/place/lookup/status" -> {
                val id = parse(req.body)?.optString("lookupId").orEmpty()
                val lookup = placeLookups[id] ?: return error(404, "地名建議已過期，請再查詢")
                if (System.currentTimeMillis() - lookup.requestedAtMillis !in 0..120_000L)
                    return error(410, "地名建議已過期，請再查詢")
                val suggestion = lookup.suggestion
                if (!lookup.completed || suggestion == null) {
                    ok(JSONObject().put("status", "pending").toString())
                } else {
                    ok(JSONObject().put("status", "ready")
                        .put("name", suggestion.displayName)
                        .put("countryCode", suggestion.countryCode ?: JSONObject.NULL)
                        .put("countryName", suggestion.countryName ?: JSONObject.NULL)
                        .put("city", suggestion.city ?: JSONObject.NULL)
                        .put("timeZoneId", suggestion.timeZoneId ?: JSONObject.NULL).toString())
                }
            }
            "/api/saved/place/add" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val point = coordinate(json) ?: return error(400, "收藏座標無效")
                val name = json.optString("name").trim()
                if (name.isBlank() || name.length > 80)
                    return error(400, "收藏名稱必須介於 1 到 80 字")
                if (prefs.places().size >= 300)
                    return error(409, "收藏地點已達 300 筆，請先整理舊收藏")
                val id = json.optString("lookupId")
                val lookup = placeLookups.remove(id)?.takeIf {
                    it.completed && it.coordinate == point &&
                        System.currentTimeMillis() - it.requestedAtMillis in 0..120_000L
                }
                val metadata = lookup?.suggestion
                prefs.addPlace(name, point, metadata?.countryCode, metadata?.countryName,
                    metadata?.city, metadata?.timeZoneId)
                accepted("已收藏「$name」，沒有瞬移或更動路線")
            }
            "/api/saved/place/enrich" -> {
                if (!placeEnrichmentBusy.compareAndSet(false, true))
                    return error(409, "收藏資訊補齊進行中")
                placeEnrichmentNote = "正在補齊舊收藏的國家／城市／時區"
                scope.launch {
                    var updated = 0
                    var scanned = 0
                    try {
                        // No network requests until the paired browser explicitly asks.
                        val candidates = prefs.places()
                            .filter(SavedPlaceMetadataPolicy::incomplete).take(5)
                        for (place in candidates) {
                            val suggestion = placeLookup.suggest(place.coordinate)
                            if (prefs.enrichSavedPlace(place.id, place.coordinate, suggestion))
                                updated++
                            scanned++
                            kotlinx.coroutines.delay(900L)
                        }
                        placeEnrichmentNote =
                            "查詢 $scanned 筆，補齊 $updated 筆；未取得的資料保持原狀"
                    } catch (e: Exception) {
                        placeEnrichmentNote = "查詢中斷：${e.message ?: "網路不可用"}"
                    } finally {
                        placeEnrichmentBusy.set(false)
                    }
                }
                accepted("已開始補齊缺少的國家、城市與時區，原地名不變")
            }
            "/api/saved/place/rename" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val name = json.optString("name").trim()
                if (name.isBlank() || name.length > 80)
                    return error(400, "收藏地點名稱需為 1 到 80 字")
                if (!prefs.renamePlace(json.optLong("id", -1), name))
                    return error(404, "收藏地點不存在")
                accepted("已更新收藏地點名稱，不會移動 GPS")
            }
            "/api/saved/place/delete" -> {
                val id = parse(req.body)?.optLong("id", -1) ?: return error(400, "JSON 無效")
                if (prefs.places().none { it.id == id })
                    return error(404, "收藏地點不存在")
                prefs.removePlace(id)
                accepted("已刪除收藏地點，不會移動 GPS")
            }
            "/api/saved/load" -> {
                if (walking()) return error(409, "行走中無法載入路線；請先停止")
                val id = parse(req.body)?.optString("id").orEmpty()
                val route = prefs.savedRoutes().firstOrNull { it.id == id }
                    ?: return error(404, "找不到收藏路線")
                prefs.loadSavedRouteIntoDraft(route)
                invalidate(route.mode)
                accepted("已載入收藏路線，請先預覽道路")
            }
            "/api/route/preview" -> {
                if (walking()) return error(409, "已有行走中的路線，請先停止")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val walkMode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val start = origin() ?: return error(409,
                    "請先在地圖選起點並按「設為起點」，或先啟動手機定位")
                val stops = points(walkMode).toList()
                val loop = json.optBoolean("loop", false)
                val anchors = runCatching {
                    LanRouteDraftPolicy.anchors(walkMode, start, stops, loop)
                }.getOrElse { return error(409, it.message ?: "路點無效") }
                if (!planning.compareAndSet(false, true))
                    return error(409, "仍在規劃上一條道路")
                invalidate(walkMode)
                LanStatus.display.value = LanStatus.display.value.copy(
                    note = "正在規劃步行道路；不會移動模擬 GPS")
                scope.launch {
                    try {
                        val detailed = ValhallaPlanner.planDetailed(anchors)
                        // Reject stale responses after draft edits or origin change.
                        if (origin() != start || points(walkMode) != stops) {
                            LanStatus.display.value = LanStatus.display.value.copy(
                                note = "起點或路點已變更，請重新規劃")
                        } else {
                            val prepared = PreparedWalkingRoute(
                                origin = start, stops = stops, geometry = detailed.geometry,
                                loop = loop, snapMetres = detailed.snapMetres,
                            )
                            val id = UUID.randomUUID().toString()
                            previews[walkMode] = WebPreview(id, prepared)
                            PreparedRouteState.set(walkMode, prepared)
                            LanStatus.display.value = LanStatus.display.value.copy(
                                note = "已完成道路預覽。確認地圖路線後再按開始行走")
                        }
                    } catch (e: Exception) {
                        LanStatus.display.value = LanStatus.display.value.copy(
                            note = "道路預覽失敗：" + (e.message ?: "請稍後重試"))
                    } finally {
                        planning.set(false)
                    }
                }
                accepted("正在規劃道路，不會移動 GPS")
            }
            "/api/route/start" -> {
                if (planning.get()) return error(409, "道路仍在規劃，請先預覽")
                if (walking()) return error(409, "已經在行走；請先停止")
                if (!mockPermissionReady()) return error(409,
                    "請先在手機授予精確定位，並於開發人員選項選取 PikminBee 為模擬位置 App")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val walkMode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val loop = json.optBoolean("loop", false)
                val available = previews[walkMode]
                val check = LanWebPreviewPolicy.canStart(
                    available?.route, available?.id, json.optString("previewId"),
                    origin(), points(walkMode), loop,
                    json.optBoolean("confirmSnap", false), usedPreviewId,
                )
                when (check) {
                    LanWebPreviewPolicy.StartCheck.MISSING ->
                        return error(409, "請先按「規劃路線」，完成預覽後再開始")
                    LanWebPreviewPolicy.StartCheck.STALE ->
                        return error(409, "路點或起點已改變，請重新預覽")
                    LanWebPreviewPolicy.StartCheck.CONFIRM_SNAP ->
                        return error(409, "有路點離道路超過 40 公尺，請確認吸附位置")
                    LanWebPreviewPolicy.StartCheck.ALREADY_USED ->
                        return error(409, "此路線已啟動過；請重新預覽")
                    LanWebPreviewPolicy.StartCheck.READY -> Unit
                }
                val preset = WalkSpeedPreset.entries.firstOrNull {
                    it.name == json.optString("speedPreset")
                } ?: return error(400, "請選擇有效速度")
                if (!starting.compareAndSet(false, true))
                    return error(409, "行走正在啟動，請勿重複點擊")
                try {
                    prefs.setWalkSpeedPreset(preset)
                    ContextCompat.startForegroundService(context,
                        MockLocationService.routeIntent(
                            context, available!!.route.geometry,
                            preset.metresPerSecond, walkMode, loop))
                    usedPreviewId = available.id
                    accepted("已要求手機沿預覽路線開始行走")
                } catch (e: Exception) {
                    error(409, "手機未能開始行走：" + (e.message ?: "請確認授權"))
                } finally {
                    starting.set(false)
                }
            }
            "/api/control" -> {
                val action = parse(req.body)?.optString("action") ?: return error(400, "指令無效")
                val state = LocationStatus.phase.value
                if (action == "pause" && state != WalkPhase.MOVING)
                    return error(409, "目前沒有行走中路線可以暫停")
                if (action == "resume" && state != WalkPhase.PAUSED)
                    return error(409, "目前沒有已暫停路線可以繼續")
                if (action == "stop" && state !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED))
                    return error(409, "目前沒有需要停止的路線")
                if (action == "clear") {
                    invalidate(WalkMode.ROUTE)
                    invalidate(WalkMode.FLOWERS)
                }
                val serviceAction = when (action) {
                    "pause" -> MockLocationService.ACTION_PAUSE_ROUTE
                    "resume" -> MockLocationService.ACTION_RESUME_ROUTE
                    "stop" -> MockLocationService.ACTION_STOP_ROUTE
                    "clear" -> MockLocationService.ACTION_CLEAR_ROUTE
                    "restore-real" -> MockLocationService.ACTION_STOP
                    else -> return error(400, "不支援的指令")
                }
                if (action == "restore-real") {
                    realRequestGeneration.incrementAndGet()
                    realFix = null
                    // Keep a previously verified physical zone for up to 30 minutes.
                    // A virtual GPS coordinate can never replace that reference.
                    realGpsState = "idle"
                }
                context.startService(MockLocationService.command(context, serviceAction))
                accepted("已送出控制指令")
            }
            else -> error(404, "未知 API")
        }
    }

    /** A phone-side opt-in on-demand read; not a background location stream. */
    private fun requestRealLocation(): LanReply {
        if (LocationStatus.mockActive.value || LocationStatus.selected.value != null)
            return error(409, "請先按「恢復真實 GPS」停止模擬定位")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED)
            return error(403, "請先在手機授予精確定位權限")
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return error(409, "手機定位服務未啟用")
        val generation = realRequestGeneration.incrementAndGet()
        realFix = null
        // A failed new fix request does not invalidate an earlier verified zone.
        realFixTimestampMillis = 0L
        realFixAccuracyMetres = -1f
        realRequestedAtMillis = System.currentTimeMillis()
        realGpsState = "locating"
        val previous = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter {
            LanRealGpsPolicy.isVerifiedFix(it.time, System.currentTimeMillis(),
                LocationCompat.isMock(it))
        }.maxByOrNull { it.time }
        if (previous != null) {
            acceptRealLocation(previous, generation)
            return accepted("已取得近期手機真實位置（未更動模擬定位或選取座標）")
        }
        var requested = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            providers.forEach { provider ->
                if (runCatching {
                    manager.getCurrentLocation(provider, null, context.mainExecutor) { fix ->
                        if (fix != null) acceptRealLocation(fix, generation)
                    }
                }.isSuccess) requested = true
            }
        }
        if (!requested) realGpsState = "unavailable"
        return accepted(if (requested) "正在取得手機真實 GPS；可能需要手機 App 在前景"
            else "無最近真實定位，請將手機 App 切至前景並開啟定位後再試")
    }

    private fun acceptRealLocation(fix: Location, generation: Int) {
        if (generation != realRequestGeneration.get() || LocationStatus.mockActive.value ||
            LocationStatus.selected.value != null ||
            !LanRealGpsPolicy.isVerifiedFix(fix.time, System.currentTimeMillis(),
                LocationCompat.isMock(fix))) return
        val point = runCatching { Coordinate(fix.latitude, fix.longitude) }.getOrNull()
            ?: return
        realFix = point
        realFixTimestampMillis = fix.time
        realFixAccuracyMetres = if (fix.hasAccuracy()) fix.accuracy else -1f
        realGpsState = "ready"
        // Only the explicitly requested, verified non-mock fix may set the
        // reference time zone; never fall back to system/Taiwan/mock time.
        scope.launch {
            val resolved = placeLookup.zoneForVerifiedPhysical(point)
            if (generation == realRequestGeneration.get() &&
                !LocationStatus.mockActive.value && LocationStatus.selected.value == null &&
                resolved != null) {
                verifiedTimeZoneReference = VerifiedRealTimeZone(resolved, fix.time)
            }
        }
    }

    private fun walking(): Boolean = LocationStatus.phase.value in
        listOf(WalkPhase.MOVING, WalkPhase.PAUSED)

    private fun mockPermissionReady(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            BeePermissions.inspect(context).selectedMockApp

    private fun invalidate(mode: WalkMode) {
        previews.remove(mode)
        PreparedRouteState.set(mode, null)
    }

    private fun maybeResolveRealZoneFromPhone() {
        // Only an explicitly non-mock, fresh fix captured by the Android App
        // can establish the reference. A browser opening Favourites is an
        // explicit user action, never a GPS tick or background location poll.
        val verified = VerifiedPhysicalFixState.fresh() ?: return
        if (verifiedTimeZoneReference?.let {
                it.physicalFixAtMillis >= verified.timestampMillis && it.freshId() != null
            } == true) return
        val now = System.currentTimeMillis()
        if (now - lastRealZoneLookupAtMillis in 0L..60_000L) return
        if (!realZoneLookupBusy.compareAndSet(false, true)) return
        lastRealZoneLookupAtMillis = now
        scope.launch {
            try {
                val zone = placeLookup.zoneForVerifiedPhysical(verified.coordinate)
                if (zone != null &&
                    VerifiedPhysicalFixState.fresh()?.timestampMillis ==
                        verified.timestampMillis) {
                    verifiedTimeZoneReference = VerifiedRealTimeZone(
                        zone, verified.timestampMillis)
                }
            } finally {
                realZoneLookupBusy.set(false)
            }
        }
    }

    private fun savedSnapshot(): String {
        maybeResolveRealZoneFromPhone()
        fun encoded(point: Coordinate) = JSONObject()
            .put("lat", point.latitude).put("lon", point.longitude)
        val places = JSONArray().apply {
            prefs.places().take(300).forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name)
                    .put("position", encoded(item.coordinate))
                    .put("countryCode", item.countryCode ?: JSONObject.NULL)
                    .put("countryName", item.countryName ?: JSONObject.NULL)
                    .put("city", item.city ?: JSONObject.NULL)
                    .put("timeZoneId", item.timeZoneId ?: JSONObject.NULL)
                    .put("createdAtMillis", item.createdAtMillis))
            }
        }
        val routes = JSONArray().apply {
            prefs.savedRoutes().take(100).forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name)
                    .put("mode", item.mode.name)
                    .put("createdAtMillis", item.createdAtMillis)
                    .put("stops", JSONArray().apply {
                        item.stops.forEach { put(encoded(it)) }
                    }))
            }
        }
        return JSONObject().put("places", places).put("routes", routes)
            .put("enrichmentBusy", placeEnrichmentBusy.get())
            .put("enrichmentNote", placeEnrichmentNote).toString()
    }

    private fun asset(path: String, mime: String): LanReply = runCatching {
        LanReply(200, context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }, mime)
    }.getOrElse { error(404, "缺少控制頁面") }

    private fun snapshot(): String {
        fun coordinates(items: List<Coordinate>): JSONArray = JSONArray().apply {
            items.forEach { put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
        }
        val position = LocationStatus.selected.value
        val geometry = LocationStatus.geometry.value
        val path = if (geometry.size > 250)
            geometry.filterIndexed { index, _ -> index % (geometry.size / 250 + 1) == 0 } +
                geometry.last() else geometry
        val routeStates = JSONObject()
        for (mode in WalkMode.entries) {
            val candidate = previews[mode]
            val prepared = candidate?.route
            if (candidate != null && prepared != null &&
                prepared.isCurrent(origin(), points(mode), prepared.loop)) {
                val sampled = prepared.geometry
                val shown = if (sampled.size > 350)
                    sampled.filterIndexed { index, _ -> index % (sampled.size / 350 + 1) == 0 } +
                        sampled.last()
                else sampled
                routeStates.put(mode.name, JSONObject()
                    .put("id", candidate.id)
                    .put("loop", prepared.loop)
                    .put("maxSnapMetres", prepared.maxSnapMetres)
                    .put("originSnapMetres", prepared.originSnapMetres)
                    .put("distanceMetres", WalkingGeometry.lengthMeters(prepared.geometry))
                    .put("geometry", coordinates(shown)))
            }
        }
        val progress = LocationStatus.progress.value
        // A newly enabled mock provider invalidates any earlier real-location request.
        val mocked = LocationStatus.mockActive.value
        if (mocked && !lastObservedMock) {
            realRequestGeneration.incrementAndGet()
            realFix = null
            // Invalidate the real-coordinate response but NOT a fresh, previously
            // verified physical time-zone reference. Never substitute mock GPS.
            realGpsState = "idle"
        }
        lastObservedMock = mocked
        val now = System.currentTimeMillis()
        val realPosition = realFix?.takeIf {
            !LocationStatus.mockActive.value && LocationStatus.selected.value == null &&
                LanRealGpsPolicy.isVerifiedFix(realFixTimestampMillis, now, false)
        }
        val gpsState = when {
            LocationStatus.mockActive.value || LocationStatus.selected.value != null -> "mock"
            realPosition != null -> "ready"
            realGpsState == "locating" && now - realRequestedAtMillis < 25_000L -> "locating"
            realGpsState == "idle" -> "idle"
            else -> "unavailable"
        }
        val realZone = verifiedTimeZoneReference
            ?.takeIf { it.freshId(now) != null }
        return JSONObject()
            .put("status", LocationStatus.display.value)
            .put("note", LanStatus.display.value.note)
            .put("position", position?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude) })
            .put("mockActive", LocationStatus.mockActive.value)
            .put("realTimeZoneId", realZone?.freshId(now) ?: JSONObject.NULL)
            .put("realTimeZoneExpiresAtMillis", realZone
                ?.let { it.physicalFixAtMillis + VerifiedRealTimeZone.MAX_AGE_MILLIS }
                ?: JSONObject.NULL)
            .put("realGpsState", gpsState)
            .put("realPosition", realPosition?.let {
                JSONObject().put("lat", it.latitude).put("lon", it.longitude)
                    .put("accuracy", if (realFixAccuracyMetres >= 0f)
                        realFixAccuracyMetres.toDouble() else JSONObject.NULL)
            })
            .put("origin", origin()?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude) })
            .put("phase", LocationStatus.phase.value.name)
            .put("mode", LocationStatus.mode.value?.name)
            .put("planning", planning.get())
            .put("mockPermissionReady", mockPermissionReady())
            .put("progress", JSONObject()
                .put("activeLeg", progress.activeLegLabel)
                .put("fraction", progress.fraction.toDouble())
                .put("travelledMetres", progress.travelledMetres)
                .put("totalMetres", progress.totalMetres)
                .put("nextWaypoint", progress.nextWaypoint)
                .put("waypointCount", progress.waypointCount)
                .put("remainingSeconds", progress.remainingSeconds)
                .put("estimatedSteps", LocationStatus.walkEstimatedSteps.value))
            .put("previews", routeStates)
            .put("speedPreset", WalkSpeedState.selected.value.name)
            .put("defaultSpeedPreset", WalkSpeedState.preferred.value.name)
            .put("routeStops", coordinates(prefs.routeDraft()))
            .put("flowerStops", coordinates(prefs.flowerDraft()))
            .put("geometry", coordinates(path))
            .toString()
    }

    private fun mode(value: String?): WalkMode? = when (value) {
        "ROUTE" -> WalkMode.ROUTE
        "FLOWERS" -> WalkMode.FLOWERS
        else -> null
    }
    private fun points(mode: WalkMode): List<Coordinate> =
        if (mode == WalkMode.FLOWERS) prefs.flowerDraft() else prefs.routeDraft()

    private fun savePoints(mode: WalkMode, list: List<Coordinate>) {
        if (mode == WalkMode.FLOWERS) prefs.saveFlowerDraft(list) else prefs.saveRouteDraft(list)
    }

    private fun parse(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
    private fun coordinate(body: String): Coordinate? = parse(body)?.let(::coordinate)
    private fun coordinate(json: JSONObject): Coordinate? =
        runCatching { Coordinate(json.getDouble("lat"), json.getDouble("lon")) }.getOrNull()

    private fun ok(body: String) = LanReply(200, body)
    private fun accepted(text: String) = LanReply(202, JSONObject().put("message", text).toString())
    private fun acceptedJson(json: String) = LanReply(202, json)
    private fun error(code: Int, text: String) = LanReply(code, JSONObject().put("error", text).toString())
}
