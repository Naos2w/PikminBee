package com.naos.pikminbee.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
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
    private val pairing = LanPairing()
    private val rateLimiter = LanRateLimiter()
    private val planning = AtomicBoolean(false)
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
                note = "僅接受同一私人 Wi-Fi 網路，配對碼不會上傳雲端",
            )
            "已開啟 Wi-Fi 網頁控制"
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
        if (req.method != "POST") return error(404, "找不到操作")
        return when (req.path) {
            "/api/teleport" -> {
                val target = coordinate(req.body) ?: return error(400, "經緯度無效")
                context.startService(MockLocationService.teleportIntent(context, target))
                accepted("已送出瞬移指令")
            }
            "/api/points/add" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val target = coordinate(json) ?: return error(400, "經緯度無效")
                val current = points(mode)
                if (current.size >= 15) return error(409, "最多只允許 15 個路點")
                savePoints(mode, current + target)
                accepted("已新增路點")
            }
            "/api/points/undo", "/api/points/clear" -> {
                val json = parse(req.body) ?: return error(400, "JSON 無效")
                val mode = mode(json.optString("mode")) ?: return error(400, "模式無效")
                val current = points(mode)
                savePoints(mode, if (req.path.endsWith("undo")) current.dropLast(1) else emptyList())
                accepted("路點已更新")
            }
            "/api/route/start" -> {
                if (!planning.compareAndSet(false, true)) return error(409, "正在規劃另一條路線")
                val json = parse(req.body)
                val mode = mode(json?.optString("mode")) ?: run {
                    planning.set(false)
                    return error(400, "模式無效")
                }
                val stops = points(mode)
                val start = LocationStatus.selected.value
                if (start == null || stops.isEmpty()) {
                    planning.set(false)
                    return error(409, "請先在手機啟動模擬定位並加入路點")
                }
                val loop = json?.optBoolean("loop", false) == true
                val speed = json?.optDouble("speed", 1.4) ?: 1.4
                scope.launch {
                    try {
                        val anchors = buildList {
                            add(start)
                            stops.forEach { if (com.naos.pikminbee.domain.WalkingGeometry.distanceMeters(last(), it) > 1.0) add(it) }
                            if (loop && com.naos.pikminbee.domain.WalkingGeometry.distanceMeters(last(), start) > 1.0) add(start)
                        }
                        val route = ValhallaPlanner.plan(anchors)
                        context.startService(MockLocationService.routeIntent(context, route, speed, mode, loop))
                    } catch (e: Exception) {
                        LanStatus.display.value = LanStatus.display.value.copy(
                            note = "道路規劃失敗：${e.message ?: "路線不可用"}")
                    } finally {
                        planning.set(false)
                    }
                }
                accepted("開始規劃行人道路")
            }
            "/api/control" -> {
                val action = parse(req.body)?.optString("action") ?: return error(400, "指令無效")
                val serviceAction = when (action) {
                    "pause" -> MockLocationService.ACTION_PAUSE_ROUTE
                    "resume" -> MockLocationService.ACTION_RESUME_ROUTE
                    "stop" -> MockLocationService.ACTION_STOP_ROUTE
                    "clear" -> MockLocationService.ACTION_CLEAR_ROUTE
                    "restore-real" -> MockLocationService.ACTION_STOP
                    else -> return error(400, "不支援的指令")
                }
                context.startService(MockLocationService.command(context, serviceAction))
                accepted("已送出控制指令")
            }
            else -> error(404, "未知 API")
        }
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
        val path = if (geometry.size > 250) geometry.filterIndexed { index, _ -> index % (geometry.size / 250 + 1) == 0 } +
            geometry.last() else geometry
        return JSONObject()
            .put("status", LocationStatus.display.value)
            .put("note", LanStatus.display.value.note)
            .put("position", position?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude) })
            .put("phase", LocationStatus.phase.value.name)
            .put("mode", LocationStatus.mode.value?.name)
            .put("planning", planning.get())
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
