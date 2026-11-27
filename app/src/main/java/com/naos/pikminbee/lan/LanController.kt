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
import com.naos.pikminbee.data.PreparedRouteState
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.RoutePlanningNotice
import com.naos.pikminbee.domain.PreparedWalkingRoute
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.domain.WalkPhase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.InstalledAppVersion
import com.naos.pikminbee.data.PlaceLookup
import com.naos.pikminbee.data.PlaceSuggestion
import com.naos.pikminbee.data.PlaceSuggestionPolicy
import com.naos.pikminbee.data.SavedPlaceMetadataPolicy
import com.naos.pikminbee.data.VerifiedRealTimeZone
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.route.ValhallaPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    val physicalSharingAllowed: Boolean = false,
    val connection: LanConnection = LanConnection(),
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
    private val installedVersion = InstalledAppVersion.read(context)
    private val placeLookup = PlaceLookup(context)
    private val pairing = LanPairing()
    private val rateLimiter = LanRateLimiter()
    private val starting = AtomicBoolean(false)
    private data class WebPreview(val id: String, val route: PreparedWalkingRoute, val draftRevision: Long)
    private val previews = ConcurrentHashMap<WalkMode, WebPreview>()
    private data class PendingPlaceLookup(
        val coordinate: Coordinate, val requestedAtMillis: Long,
        @Volatile var suggestion: PlaceSuggestion? = null,
        @Volatile var completed: Boolean = false,
    )
    private val placeLookups = ConcurrentHashMap<String, PendingPlaceLookup>()
    private val placeEnrichmentBusy = AtomicBoolean(false)
    @Volatile private var placeEnrichmentNote = ""
    @Volatile private var usedPreviewId: String? = null
    // Real phone location is only sampled after an explicitly approved browser action.
    // Never cache or publish a test-provider fix as a real physical position.
    private val realRequestGeneration = AtomicInteger()
    @Volatile private var realRequestedAtMillis = 0L
    @Volatile private var realFix: Coordinate? = null
    @Volatile private var realFixTimestampMillis = 0L
    @Volatile private var realFixAccuracyMetres = -1f
    @Volatile private var realGpsState = "idle"
    // Session-bound, explicit phone consent. Never persist accurate physical
    // coordinates or the sharing grant in SharedPreferences/browser storage.
    @Volatile private var physicalShareSessionToken: String? = null
    @Volatile private var physicalSnapshot: LanPhysicalSnapshot? = null
    @Volatile private var verifiedTimeZoneReference: VerifiedRealTimeZone? = null
    @Volatile private var lastObservedMock = false
    private fun origin(): Coordinate? = RouteDraftState.snapshot().origin ?: LocationStatus.selected.value
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
                connection = pairing.connection(),
                note = "僅接受可互通的私人區網，配對碼不會上傳雲端",
            )
            "已開啟電腦網頁控制"
        }.getOrElse {
            pairing.revoke()
            LanStatus.display.value = LanDisplay(note = "無法開啟：${it.message}", connection = LanConnection(LanConnectionPhase.ERROR))
            "無法啟動 Wi-Fi 服務：${it.message}"
        }
    }

    private fun refreshConnectionDisplay() {
        if (server == null) return
        val connection = pairing.connection()
        LanStatus.display.update { current ->
            if (!current.active) current else current.copy(
                connection = connection,
                pendingDevice = connection.pendingDevice,
                pairingPin = if (connection.showPin) pairing.currentPin() else "",
            )
        }
    }

    /** End browser authorisation, not the Wi-Fi listener or route. */
    fun disconnectBrowser() {
        if (server == null) return
        pairing.disconnect()
        revokePhysicalSharing("已撤銷瀏覽器授權；可重新配對")
        refreshConnectionDisplay()
    }

    fun approvePending(sharePhysical: Boolean = false, approvalId: String? = null) {
        val renewal = pairing.connection().renewal
        val approved = pairing.approve(sharePhysical, approvalId)
        LanStatus.display.value = LanStatus.display.value.copy(
            pendingDevice = "",
            note = if (!approved) "沒有待核准的裝置"
                else if (renewal) "已核准續期，等待原網頁完成更新權杖"
                else "已核准網頁裝置，等待瀏覽器建立工作階段")
        refreshConnectionDisplay()
    }

    fun denyPending() {
        val renewal = LanStatus.display.value.pendingDevice.startsWith("續期")
        pairing.deny()
        LanStatus.display.value = LanStatus.display.value.copy(
            pendingDevice = "",
            note = if (renewal) "已拒絕網頁續期" else "已拒絕裝置配對")
        refreshConnectionDisplay()
    }

    /** Called from an explicit on-phone consent, not a browser or listener startup. */
    @Synchronized fun approvePhysicalSharing() {
        grantPhysicalSharing(pairing.activeSessionToken())
    }

    private fun grantPhysicalSharing(session: String?) {
        val fineGranted = ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (session == null || session != pairing.activeSessionToken() || !fineGranted || server == null) {
            LanStatus.display.value = LanStatus.display.value.copy(
                physicalSharingAllowed = false,
                note = if (session == null) "請先完成網頁配對與手機核准，再同意分享真實位置"
                    else "請先在 Android 授予精確定位權限")
            return
        }
        physicalShareSessionToken = session
        // VerifiedPhysicalFixState receives non-mock samples only, in the phone
        // UI's foreground LocationManager listener. A mock provider never updates it.
        physicalSnapshot = com.naos.pikminbee.ui.VerifiedPhysicalFixState.fresh()
            ?.let { LanPhysicalSnapshot(it.coordinate, it.timestampMillis, it.accuracyMetres) }
        LanStatus.display.value = LanStatus.display.value.copy(
            physicalSharingAllowed = true,
            note = if (physicalSnapshot != null)
                "已允許這次配對瀏覽器查看最近真實 GPS（最多至定位後 30 分鐘）"
            else "已允許分享，但尚無可信真實定位；請先切回真實 GPS 並取得定位")
    }

    /** Apply the explicit choice only to the newly issued token. Never inherit consent. */
    @Synchronized private fun applyPairingConsent(status: LanPairing.ApprovalStatus) {
        if (status.token == null || status.token != pairing.activeSessionToken() || server == null) return
        revokePhysicalSharing("網頁配對已核准；真實位置未分享")
        if (status.sharePhysical) grantPhysicalSharing(status.token)
    }

    /** Revocation also cancels in-flight GPS requests and removes precise data. */
    @Synchronized fun revokePhysicalSharing(note: String = "已撤銷真實 GPS 分享") {
        physicalShareSessionToken = null
        physicalSnapshot = null
        realRequestGeneration.incrementAndGet()
        realFix = null
        realFixTimestampMillis = 0L
        realGpsState = "idle"
        LanStatus.display.value = LanStatus.display.value.copy(
            physicalSharingAllowed = false, note = note)
    }

    fun stop(reason: String = "Wi-Fi 控制已關閉") {
        realRequestGeneration.incrementAndGet()
        physicalShareSessionToken = null
        physicalSnapshot = null
        realFix = null
        verifiedTimeZoneReference = null
        realGpsState = "idle"
        previews.clear()
        placeLookups.clear()
        placeEnrichmentNote = ""
        usedPreviewId = null
        server?.stop()
        server = null
        bound = null
        pairing.revoke()
        LanStatus.display.value = LanDisplay(note = reason, connection = LanConnection(
            if (reason.contains("斷線") || reason.contains("切換")) LanConnectionPhase.ERROR
            else LanConnectionPhase.CLOSED))
    }

    fun checkNetwork() {
        if (server == null) return
        refreshConnectionDisplay()
        synchronized(this) {
            if (physicalShareSessionToken != null &&
                physicalShareSessionToken != pairing.activeSessionToken()) {
                revokePhysicalSharing("配對工作階段已到期；真實 GPS 分享已關閉")
            }
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNetworkCheck < 10_000L) return
        lastNetworkCheck = now
        if (LanWifiAddress.resolve(context) != bound) {
            stop("Wi-Fi 已切換或斷線，網頁控制自動關閉")
        }
    }

    fun close() {
        if (server != null) stop()
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
        if (req.method == "GET" && req.path == "/bright-dark-palette.json") {
            return asset("lan/bright-dark-palette.json", "application/json; charset=utf-8")
        }
        if (req.method == "GET" && req.path == "/map-ui-policy.mjs") {
            return asset("lan/map-ui-policy.mjs", "application/javascript; charset=utf-8")
        }
        if (req.method == "GET" && req.path == "/coordinate-format.mjs") {
            return asset("lan/coordinate-format.mjs", "application/javascript; charset=utf-8")
        }
        // Only two-letter flag filenames are served; no arbitrary asset paths.
        if (req.method == "GET" && req.path.matches(Regex("/flags/[a-z]{2}\\.png"))) {
            return runCatching {
                LanReply(200, "", "image/png",
                    binaryBody = context.assets.open("lan${req.path}").use { it.readBytes() })
            }.getOrElse { error(404, "國旗圖片不存在") }
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
            refreshConnectionDisplay()
            return acceptedJson(JSONObject().put("pendingId", result.pendingId).toString())
        }
        if (req.method == "GET" && req.path == "/api/pair/status") {
            val status = pairing.poll(req.remoteAddress, req.pendingId)
            return when (status.state) {
                "approved" -> {
                    // The new browser gets only the choice explicitly confirmed for its request.
                    applyPairingConsent(status)
                    refreshConnectionDisplay()
                    ok(JSONObject().put("token", status.token)
                        .put("expiresInSeconds", pairing.sessionDurationSeconds).toString())
                }
                "pending" -> acceptedJson(JSONObject().put("status", "pending").toString())
                else -> error(403, "裝置尚未核准、已拒絕或配對已過期")
            }
        }
        if (!pairing.authorised(req.token, req.remoteAddress)) return error(401, "請在手機取得配對碼後重新連線")
        if (req.method == "POST" && req.path == "/api/session/renew") {
            val renewal = pairing.requestRenewal(req.token, req.remoteAddress)
            if (renewal.pendingId == null)
                return error(409, "已有待核准請求，或目前會話不允許續期")
            LanStatus.display.value = LanStatus.display.value.copy(
                pendingDevice = "續期目前裝置 · ${req.remoteAddress}",
                note = "網頁要求再延長 2 小時；須在手機明確核准")
            refreshConnectionDisplay()
            return acceptedJson(JSONObject().put("pendingId", renewal.pendingId).toString())
        }
        if (req.method == "POST" && req.path == "/api/session/renew/status") {
            val body = parse(req.body) ?: return error(400, "續期請求無效")
            val renewal = pairing.pollRenewal(
                req.remoteAddress, body.optString("pendingId"), req.token
            )
            return when (renewal.state) {
                "approved" -> {
                    // Renewed tokens get only the new explicit on-phone choice.
                    applyPairingConsent(renewal)
                    refreshConnectionDisplay()
                    ok(JSONObject().put("token", renewal.token)
                        .put("expiresInSeconds", pairing.sessionDurationSeconds).toString())
                }
                "pending" -> acceptedJson(JSONObject().put("status", "pending").toString())
                else -> error(403, "續期已拒絕、逾時或原本會話失效")
            }
        }
        if (req.path.startsWith("/api/flowers/"))
            return error(410, "尋花模式已移除；舊資料仍保留在手機上")
        if (req.method == "GET" && req.path == "/api/state") {
            if (!pairing.markStatePoll(req.token, req.remoteAddress))
                return error(401, "配對已失效，請重新配對")
            refreshConnectionDisplay()
            return ok(snapshot(req.token, req.remoteAddress))
        }
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
            "/api/points/add", "/api/points/reorder", "/api/points/remove", "/api/points/replace",
            "/api/points/undo", "/api/points/clear" -> {
                if (walking() || starting.get() || RouteDraftState.planning.value)
                    return error(409, "路線規劃或行走中，不能修改路點")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val draft = RouteDraftState.snapshot()
                val expected = if (json.has("expectedRevision")) json.optLong("expectedRevision", -1)
                    else draft.revision
                if (expected != draft.revision)
                    return error(409, "路點已在其他介面變更，請重新確認")
                val index = json.optInt("index", -1)
                val current = draft.stops
                val result = when {
                    req.path.endsWith("add") -> {
                        val target = coordinate(json) ?: return error(400, "經緯度無效")
                        LanRouteDraftPolicy.add(mode, current, target, origin())
                    }
                    req.path.endsWith("reorder") -> {
                        if (json.has("from") || json.has("to")) {
                            if (!json.has("expectedRevision")) return error(400, "缺少路點版本")
                            LanRouteDraftPolicy.moveTo(current, json.optInt("from", -1), json.optInt("to", -1))
                        } else LanRouteDraftPolicy.reorder(current, index, json.optInt("offset", 0))
                    }
                    req.path.endsWith("replace") -> {
                        val target = coordinate(json) ?: return error(400, "經緯度無效")
                        LanRouteDraftPolicy.replace(current, index, target, origin())
                    }
                    req.path.endsWith("remove") -> LanRouteDraftPolicy.remove(current, index)
                    req.path.endsWith("undo") -> LanRouteDraftPolicy.Addition(
                        current.dropLast(1), true, "路點已更新")
                    else -> LanRouteDraftPolicy.Addition(emptyList(), true, "路點已清除")
                }
                if (!result.accepted) return error(409, result.message)
                if (walking() || starting.get() ||
                    !prefs.saveRouteDraftIfCurrent(expected, result.stops))
                    return error(409, "路點已變更，或目前無法編輯，請重新確認")
                invalidate(mode)
                accepted(result.message)
            }
            "/api/speed" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val profile = speedProfile(json) ?: return error(400, "速度設定無效，請確認 0.8–20 km/h")
                if (walking()) {
                    val result = runCatching {
                        context.startService(MockLocationService.speedIntent(context, profile))
                    }
                    if (result.isFailure) return error(409, "手機無法套用速度，請檢查背景權限")
                } else prefs.setWalkSpeedProfile(profile)
                accepted("已要求設定本次速度：" + profile.display)
            }
            "/api/speed/default" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val profile = speedProfile(json)?.takeUnless { it.mode == WalkSpeedMode.RANGE }
                    ?: return error(400, "不支援的預設速度")
                prefs.setDefaultWalkSpeedProfile(profile)
                accepted("下次開啟 App 的預設速度已設為：${profile.display}")
            }
            "/api/location/real" -> requestRealLocation(req.token)
            "/api/origin", "/api/route/options" -> {
                if (walking() || starting.get() || RouteDraftState.planning.value)
                    return error(409, "規劃或行走中無法修改起點或路線設定")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val draft = RouteDraftState.snapshot()
                val nextOrigin = if (req.path == "/api/origin")
                    coordinate(json) ?: return error(400, "起點經緯度無效") else draft.origin
                val loop = if (req.path == "/api/route/options") json.optBoolean("loop", draft.loop) else draft.loop
                if (loop && draft.stops.size > 14) return error(409, "循環路線最多 14 個目的地")
                synchronized(RouteDraftState) {
                    if (!prefs.saveRouteOptionsIfCurrent(json.optLong("expectedRevision", draft.revision), nextOrigin, loop))
                        return error(409, "路線已變更，請重新確認")
                    invalidate(WalkMode.ROUTE)
                }
                accepted("已更新路線設定；尚未移動 GPS")
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
                val existing = prefs.places().firstOrNull { it.coordinate == point }
                if (existing != null) {
                    if (metadata != null)
                        prefs.enrichSavedPlace(existing.id, point, metadata)
                    return accepted("此位置已收藏為「${existing.name}」，原名稱保留")
                }
                prefs.addPlace(name, point, metadata?.countryCode, metadata?.countryName,
                    metadata?.city, metadata?.timeZoneId)
                accepted("已自動收藏「$name」，沒有瞬移或更動路線")
            }
            "/api/saved/place/enrich" -> {
                if (!placeEnrichmentBusy.compareAndSet(false, true))
                    return error(409, "收藏資訊補齊進行中")
                placeEnrichmentNote = "正在補齊舊收藏的自動地名／國旗／城市／時區"
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
                accepted("已開始補齊地名／國旗／時區；自訂收藏名稱不會被覆寫")
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
            "/api/saved/route/add" -> {
                if (walking() || starting.get() || RouteDraftState.planning.value)
                    return error(409, "請先完成或取消目前規劃")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val name = json.optString("name").trim()
                if (name.isBlank() || name.length > 60) return error(400, "路線名稱需為 1 到 60 字")
                synchronized(RouteDraftState) {
                    val draft = RouteDraftState.snapshot()
                    if (draft.revision != json.optLong("expectedRevision", -1) || RouteDraftState.planning.value)
                        return error(409, "路線已變更，請重新確認收藏")
                    if (draft.stops.isEmpty()) return error(400, "請先加入路點")
                    val savedOrigin = draft.origin ?: previews[WalkMode.ROUTE]?.takeIf {
                        it.draftRevision == draft.revision
                    }?.route?.origin ?: origin()
                    runCatching { prefs.addSavedRoute(name, WalkMode.ROUTE, draft.stops, savedOrigin, draft.loop) }
                        .getOrElse { return error(409, it.message ?: "無法儲存路線") }
                }
                accepted("已收藏路線；未變更定位")
            }
            "/api/saved/route/rename" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val name = json.optString("name").trim()
                if (name.isBlank() || name.length > 60) return error(400, "路線名稱需為 1 到 60 字")
                val updated = runCatching { prefs.renameSavedRoute(json.optString("id"), name) }
                    .getOrElse { return error(409, it.message ?: "無法改名") }
                if (!updated) return error(404, "收藏路線不存在")
                accepted("已更新收藏路線名稱")
            }
            "/api/saved/route/delete" -> {
                val id = parse(req.body)?.optString("id").orEmpty()
                val deleted = runCatching { prefs.deleteSavedRoute(id) }
                    .getOrElse { return error(409, it.message ?: "無法刪除路線") }
                if (!deleted) return error(404, "收藏路線不存在")
                accepted("已刪除收藏路線；草稿與定位不變")
            }
            "/api/saved/load" -> {
                if (walking() || starting.get() || RouteDraftState.planning.value)
                    return error(409, "規劃或行走中無法載入路線")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val route = prefs.savedRoutes().firstOrNull { it.id == json.optString("id") }
                    ?: return error(404, "找不到收藏路線")
                synchronized(RouteDraftState) {
                    val draft = RouteDraftState.snapshot()
                    if (!prefs.loadSavedRouteDraftIfCurrent(json.optLong("expectedRevision", draft.revision), route))
                        return error(409, "路線已變更，請重新確認")
                    invalidate(WalkMode.ROUTE)
                }
                accepted("已載入路線起點、路點與設定；請先預覽道路")
            }
            "/api/route/preview" -> {
                if (walking()) return error(409, "已有行走中的路線，請先停止")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val walkMode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val start = origin() ?: return error(409,
                    "請先在地圖選起點並按「設為起點」，或先啟動手機定位")
                val draft = RouteDraftState.snapshot()
                val stops = draft.stops
                val loop = json.optBoolean("loop", false)
                if (loop != draft.loop) return error(409, "路線設定已變更，請重新確認")
                val anchors = runCatching {
                    LanRouteDraftPolicy.anchors(walkMode, start, stops, loop)
                }.getOrElse { return error(409, it.message ?: "路點無效") }
                val owner = RouteDraftState.beginPlanning(draft.revision)
                    ?: return error(409, "路點已變更或仍在規劃，請重新確認")
                invalidate(walkMode)
                LanStatus.display.value = LanStatus.display.value.copy(
                    note = "正在規劃步行道路；不會移動模擬 GPS")
                scope.launch {
                    try {
                        val detailed = ValhallaPlanner.planDetailed(anchors) { RouteDraftState.isPlanning(owner) }
                        synchronized(RouteDraftState) {
                            if (RouteDraftState.isPlanning(owner) && RouteDraftState.snapshot().revision == draft.revision) {
                                val prepared = PreparedWalkingRoute(
                                    origin = start, stops = stops, geometry = detailed.geometry,
                                    loop = loop, snapMetres = detailed.snapMetres,
                                )
                                val id = UUID.randomUUID().toString()
                                previews[walkMode] = WebPreview(id, prepared, draft.revision)
                                PreparedRouteState.set(walkMode, prepared)
                                RouteDraftState.finishPlanning(owner, RoutePlanningNotice("已完成道路預覽"))
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        RouteDraftState.finishPlanning(owner, RoutePlanningNotice(
                            "道路規劃失敗：" + (e.message ?: "請稍後重試"), true))
                    } finally {
                        RouteDraftState.finishPlanning(owner)
                    }
                }.invokeOnCompletion { RouteDraftState.finishPlanning(owner) }
                accepted("正在規劃道路，不會移動 GPS")
            }
            "/api/route/cancel-preview" -> {
                if (!RouteDraftState.cancelPlanning()) return error(409, "目前沒有規劃中的路線")
                accepted("已取消規劃；不會採用舊回應")
            }
            "/api/route/start" -> {
                if (RouteDraftState.planning.value) return error(409, "道路仍在規劃，請先預覽")
                if (walking()) return error(409, "已經在行走；請先停止")
                if (!mockPermissionReady()) return error(409,
                    "請先在手機授予精確定位，並於開發人員選項選取 PikminBee 為模擬位置 App")
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val walkMode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val loop = json.optBoolean("loop", false)
                val available = previews[walkMode]?.takeIf {
                    it.draftRevision == RouteDraftState.snapshot().revision &&
                        PreparedRouteState.get(walkMode) == it.route
                }
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
                val profile = speedProfile(json) ?: return error(400, "請選擇有效速度（0.8–20 km/h）")
                if (!starting.compareAndSet(false, true))
                    return error(409, "行走正在啟動，請勿重複點擊")
                try {
                    prefs.setWalkSpeedProfile(profile)
                    ContextCompat.startForegroundService(context,
                        MockLocationService.routeIntent(
                            context, available!!.route.geometry,
                            profile.nominalMps, walkMode, loop))
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

    /** Request-specific, session-bound snapshot. No continuous real GPS stream. */
    private fun visiblePhysicalSnapshot(
        token: String?, nowMillis: Long = System.currentTimeMillis(),
    ): LanPhysicalSnapshot? {
        val value = physicalSnapshot
        if (!LanPhysicalSharingPolicy.isFresh(value, nowMillis)) {
            physicalSnapshot = null
            return null
        }
        return value?.takeIf {
            LanPhysicalSharingPolicy.mayExpose(
                token, physicalShareSessionToken, it, nowMillis)
        }
    }

    /** On-demand read after explicit PHONE consent + paired browser request. */
    private fun requestRealLocation(token: String?): LanReply {
        if (token.isNullOrBlank() || token != physicalShareSessionToken)
            return error(403, "請先在手機 Wi-Fi 控制設定中同意分享真實位置")
        if (LocationStatus.mockActive.value || LocationStatus.selected.value != null) {
            if (visiblePhysicalSnapshot(token) != null)
                return accepted("可查看先前驗證的真實位置快照；目前為虛擬 GPS，無法取得新的真實位置")
            return error(409, "目前為虛擬 GPS 且沒有可信快照；請先切回真實 GPS 取得位置")
        }
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
        realFixAccuracyMetres = if (fix.hasAccuracy() &&
            fix.accuracy.isFinite() && fix.accuracy >= 0f) fix.accuracy else -1f
        // Only this explicit request, accepted after phone-level consent, may
        // create/refresh the shared snapshot. No background or GPS-tick refresh.
        if (physicalShareSessionToken != null &&
            physicalShareSessionToken == pairing.activeSessionToken()) {
            physicalSnapshot = LanPhysicalSnapshot(point, fix.time,
                realFixAccuracyMetres.takeIf { it >= 0f })
        }
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

    private fun savedSnapshot(): String {
        // Destination clocks need no phone-position time zone or external lookup.
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
                    .put("origin", item.origin?.let(::encoded) ?: JSONObject.NULL)
                    .put("loop", item.loop)
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

    private fun snapshot(requesterToken: String?, requesterPeer: String): String {
        fun coordinates(items: List<Coordinate>): JSONArray = JSONArray().apply {
            items.forEach { put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
        }
        val position = LocationStatus.selected.value
        val geometry = LocationStatus.geometry.value
        val path = if (geometry.size > 250)
            geometry.filterIndexed { index, _ -> index % (geometry.size / 250 + 1) == 0 } +
                geometry.last() else geometry
        val draft = RouteDraftState.snapshot()
        val routeStates = JSONObject()
        for (mode in WalkMode.entries) {
            val candidate = previews[mode]
            val prepared = candidate?.route
            if (candidate != null && prepared != null && candidate.id != usedPreviewId &&
                candidate.draftRevision == draft.revision && !RouteDraftState.planning.value &&
                PreparedRouteState.get(mode) == prepared && prepared.matchesDraft(draft.stops, draft.loop)) {
                val sampled = prepared.geometry
                val shown = if (sampled.size > 350)
                    sampled.filterIndexed { index, _ -> index % (sampled.size / 350 + 1) == 0 } +
                        sampled.last()
                else sampled
                routeStates.put(mode.name, JSONObject()
                    .put("id", candidate.id)
                    .put("loop", prepared.loop)
                    .put("canStart", prepared.isCurrent(origin(), draft.stops, draft.loop))
                    .put("maxSnapMetres", prepared.maxSnapMetres)
                    .put("originSnapMetres", prepared.originSnapMetres)
                    .put("distanceMetres", WalkingGeometry.lengthMeters(prepared.geometry))
                    .put("geometry", coordinates(shown)))
            }
        }
        val progress = LocationStatus.progress.value
        val phase = LocationStatus.phase.value
        val routeSegments = if (LocationStatus.mode.value == WalkMode.ROUTE &&
            phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED))
            com.naos.pikminbee.domain.RouteMapSegments.from(geometry, progress)
            else com.naos.pikminbee.domain.RouteMapSegments()
        fun displayPath(points: List<Coordinate>): List<Coordinate> = if (points.size > 250)
            points.filterIndexed { index, _ -> index % (points.size / 250 + 1) == 0 } + points.last()
            else points
        val appliedKmh = com.naos.pikminbee.domain.RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
            phase, LocationStatus.appliedSpeedKmh.value)
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
        val realSnapshot = visiblePhysicalSnapshot(requesterToken, now)
        val mockEnabled = LocationStatus.mockActive.value ||
            LocationStatus.selected.value != null
        val gpsState = when {
            requesterToken != physicalShareSessionToken || requesterToken.isNullOrBlank() -> "locked"
            mockEnabled && realSnapshot != null -> "historical"
            mockEnabled -> "mock"
            realSnapshot != null && now - realSnapshot.capturedAtMillis <= 120_000L -> "ready"
            realSnapshot != null -> "historical"
            realGpsState == "locating" && now - realRequestedAtMillis < 25_000L -> "locating"
            realGpsState == "idle" -> "idle"
            else -> "unavailable"
        }
        val realZone = verifiedTimeZoneReference
            ?.takeIf { it.freshId(now) != null }
        return JSONObject()
            .put("sessionExpiresInSeconds", pairing.secondsRemaining(requesterToken, requesterPeer))
            .put("connectionPhase", pairing.connection().phase.name)
            .put("appVersionName", installedVersion.name ?: JSONObject.NULL)
            .put("appVersionCode", installedVersion.code ?: JSONObject.NULL)
            .put("appEnvironment", installedVersion.environment)
            .put("appVersionDisplay", installedVersion.displayName)
            .put("status", LocationStatus.display.value)
            .put("note", LanStatus.display.value.note)
            .put("position", position?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude) })
            .put("mockActive", LocationStatus.mockActive.value)
            .put("realTimeZoneId", realZone?.freshId(now) ?: JSONObject.NULL)
            .put("realTimeZoneExpiresAtMillis", realZone
                ?.let { it.physicalFixAtMillis + VerifiedRealTimeZone.MAX_AGE_MILLIS }
                ?: JSONObject.NULL)
            .put("realGpsState", gpsState)
            .put("realLocationSharingApproved",
                requesterToken != null && requesterToken == physicalShareSessionToken)
            .put("realPosition", realSnapshot?.let {
                JSONObject().put("lat", it.coordinate.latitude)
                    .put("lon", it.coordinate.longitude)
                    .put("accuracy", it.accuracyMetres?.toDouble() ?: JSONObject.NULL)
                    .put("capturedAtMillis", it.capturedAtMillis)
                    .put("expiresAtMillis",
                        it.capturedAtMillis + LanPhysicalSharingPolicy.MAX_AGE_MILLIS)
                    .put("historical", mockEnabled ||
                        now - it.capturedAtMillis > 120_000L)
            })
            .put("origin", origin()?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude) })
            .put("phase", phase.name)
            .put("routeArrivalId", LocationStatus.routeArrivalId.value
                ?.takeIf { phase == com.naos.pikminbee.domain.WalkPhase.PARKED }
                ?: JSONObject.NULL)
            .put("mode", LocationStatus.mode.value?.name)
            .put("activeLegGeometry", coordinates(displayPath(routeSegments.active)))
            .put("completedGeometry", coordinates(displayPath(routeSegments.completed)))
            .put("planning", RouteDraftState.planning.value)
            .put("planningNotice", RouteDraftState.planningNotice.value.text)
            .put("planningError", RouteDraftState.planningNotice.value.error)
            .put("routeLoop", draft.loop)
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
            .put("speedMode", WalkSpeedState.profile.value.mode.name)
            .put("speedKmh", WalkSpeedState.profile.value.fixedKmh)
            .put("speedMinKmh", WalkSpeedState.profile.value.minKmh)
            .put("speedMaxKmh", WalkSpeedState.profile.value.maxKmh)
            .put("speedAppliedKmh", appliedKmh)
            .put("speedAppliedDisplay", com.naos.pikminbee.domain.RouteSurfaceStatusPolicy
                .appliedRouteSpeedLabel(phase, appliedKmh) ?: "")
            .put("defaultSpeedPreset", WalkSpeedState.preferred.value.name)
            .put("defaultSpeedMode", WalkSpeedState.preferredProfile.value.mode.name)
            .put("defaultSpeedKmh", WalkSpeedState.preferredProfile.value.fixedKmh)
            .put("defaultSpeedDisplay", WalkSpeedState.preferredProfile.value.display)
            .put("routeRevision", draft.revision)
            .put("routeStops", coordinates(draft.stops))
            .put("geometry", coordinates(path))
            .toString()
    }

    /** A single validation boundary for all paired-Web speed operations. */
    private fun speedProfile(json: JSONObject): WalkSpeedProfile? = runCatching {
        val mode = json.optString("speedMode", "PRESET")
        val preset = WalkSpeedPreset.entries.firstOrNull {
            it.name == json.optString("speedPreset")
        }
        when (mode) {
            "PRESET" -> WalkSpeedProfile.preset(preset
                ?: throw IllegalArgumentException("未知速度預設"))
            "FIXED" -> WalkSpeedProfile.fixed(
                json.getDouble("speedKmh"), WalkSpeedState.selected.value)
            "RANGE" -> WalkSpeedProfile.range(
                json.getDouble("speedMinKmh"), json.getDouble("speedMaxKmh"),
                WalkSpeedState.selected.value)
            else -> throw IllegalArgumentException("未知速度模式")
        }
    }.getOrNull()

    private fun mode(value: String?): WalkMode? = when (value) {
        "ROUTE" -> WalkMode.ROUTE
        else -> null
    }
    private fun points(mode: WalkMode): List<Coordinate> =
        if (mode == WalkMode.ROUTE) prefs.routeDraft() else emptyList()

    private fun savePoints(mode: WalkMode, list: List<Coordinate>) {
        if (mode == WalkMode.ROUTE) prefs.saveRouteDraft(list)
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
