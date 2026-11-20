package com.naos.pikminbee.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.FlowerMapDraft
import com.naos.pikminbee.domain.RouteEstimate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.route.ValhallaPlanner
import kotlinx.coroutines.launch

/**
 * Shared route editor for pedestrian travel and manually marked flower stops.
 * Actual game flower discovery is not guessed from public OSM POI records.
 */
@Composable
fun JourneyControls(
    mode: WalkMode,
    stops: List<Coordinate>,
    origin: Coordinate?,
    activeMode: WalkMode?,
    phase: WalkPhase,
    speedPreset: WalkSpeedPreset,
    onSpeedSelected: (WalkSpeedPreset) -> Unit,
    foreground: Color,
    muted: Color,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onMessage: (String) -> Unit,
    onAskLocation: () -> Unit,
    onPreviewRoad: (List<Coordinate>) -> Unit = {},
    onPreviewShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val flower = mode == WalkMode.FLOWERS
    val active = activeMode == mode
    var loop by remember { mutableStateOf(flower) }
    var planning by remember { mutableStateOf(false) }
    var requestRevision by remember { mutableIntStateOf(0) }
    var previewDistance by remember { mutableStateOf<Double?>(null) }
    var previewNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stops, loop, mode) {
        // GPS fixes can jitter while Valhalla is still replying. Invalidate only
        // actual route edits, never every transient origin-position update.
        // Re-opening the sheet must not erase a successfully drawn road.
        requestRevision++
        previewDistance = null
        previewNotice = null
    }
    fun requestedPoints(start: Coordinate): List<Coordinate> =
        if (flower) FlowerMapDraft.routePoints(start, stops, loop) else buildList {
            add(start)
            stops.forEach { stop ->
                if (WalkingGeometry.distanceMeters(last(), stop) > 1.0) add(stop)
            }
        }
    val canPlan = origin?.let { start ->
        runCatching { requestedPoints(start).size >= 2 }.getOrDefault(false)
    } ?: false

    fun previewOrStart(startWalking: Boolean) {
        if (planning) return
        val start = origin ?: run {
            onMessage(if (flower) "請先在地圖設為尋花起點" else "請先選取路徑起點")
            return
        }
        if (startWalking && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onAskLocation()
            return
        }
        if (startWalking && !BeePermissions.inspect(context).selectedMockApp) {
            onMessage("請先在 Android 開發人員選項選取 PikminBee 作為模擬位置 App")
            return
        }
        val points = try { requestedPoints(start) } catch (e: IllegalArgumentException) {
            onMessage(e.message ?: "路點不正確")
            return
        }
        if (points.size !in 2..16) {
            onMessage("請選取不同於起點的路點")
            return
        }
        val revision = requestRevision
        planning = true
        previewNotice = "正在查詢步行道路…"
        onMessage("正在查詢步行道路，失敗不會改成直線行走")
        coroutineScope.launch {
            try {
                val geometry = ValhallaPlanner.plan(points)
                if (revision != requestRevision) return@launch
                onPreviewRoad(geometry)
                previewDistance = WalkingGeometry.lengthMeters(geometry)
                previewNotice = null
                if (startWalking) {
                    ContextCompat.startForegroundService(
                        context, MockLocationService.routeIntent(
                            context, geometry, WalkSpeedState.selected.value.metresPerSecond,
                            mode, loop = flower && loop
                        ),
                    )
                    onMessage("道路路線已取得，開始移動（${geometry.size} 個節點）")
                } else {
                    onMessage("已繪製步行道路預覽 · 沒有啟動模擬行走")
                    onPreviewShown()
                }
            } catch (e: Exception) {
                val notice = e.message ?: "規劃步行道路失敗"
                previewNotice = "道路預覽失敗：$notice"
                onMessage(previewNotice!!)
            } finally {
                planning = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (flower) "尋花巡迴" else "多點道路路徑",
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
        Text(
            if (flower) "地圖先選點並確認加入，預覽道路後再開始；花點來源不代表遊戲即時狀態。"
            else "點地圖立即新增下一個編號路點；按「上一步」可重選前一點，點地圖上的編號可刪除或改位置。",
            color = muted, fontSize = 12.sp
        )
        Text("已加入 ${stops.size} 個路點 · 起點 ${origin?.label() ?: "尚未設定"}",
            color = muted, fontSize = 12.sp)
        if (!flower && stops.isNotEmpty()) {
            Text("路徑順序  " + stops.indices.joinToString("  →  ") { "${it + 1}" },
                color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("目前藍／橘色位置為起點 · 綠色 1 為第一目的地 · 紅色為終點",
                color = muted, fontSize = 12.sp)
        }
        Text("本次行走速度（個人預設請至設定）", color = muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            WalkSpeedPreset.entries.forEach { option ->
                FilterChip(
                    selected = speedPreset == option,
                    onClick = { onSpeedSelected(option) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            "${option.label}\n${option.kilometresPerHour} km/h",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    },
                )
            }
        }

        if (flower) FilterChip(
            selected = loop,
            onClick = {
                loop = !loop
                onPreviewRoad(emptyList())
            },
            label = { Text("循環路線", fontSize = 12.sp) },
        )

        run {
            OutlinedButton(
                onClick = { previewOrStart(startWalking = false) },
                enabled = !planning && canPlan &&
                    (!active || phase !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text(if (planning) "道路規劃中…" else "先預覽步行道路（不移動）", fontSize = 12.sp) }
            if (!canPlan) {
                Text(
                    if (origin == null) "尚未取得路線起點：請先授權目前定位或設定尋花起點。"
                    else "請先在地圖選取至少一個不同於起點的目的地。",
                    color = muted, fontSize = 12.sp,
                )
            }
            previewNotice?.let { notice ->
                Text(notice, color = foreground, fontSize = 12.sp)
            }
            previewDistance?.let { metres ->
                val estimate = RouteEstimate.fromDistance(metres, speedPreset)
                Text(
                    "道路距離 ${estimate.distanceLabel} · 預估行走 ${estimate.durationLabel}",
                    color = foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "依 ${speedPreset.kilometresPerHour} km/h 估算，不含休息及系統延遲；改變速度會同步更新。",
                    color = muted, fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { previewOrStart(startWalking = true) },
                enabled = !planning && canPlan &&
                    (!active || phase !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)),
                modifier = Modifier.weight(1.3f).height(46.dp),
                colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9F5E0C),
                        contentColor = Color.White,
                    ),
            ) { Text(if (planning) "規劃中…" else "開始行走", fontSize = 12.sp) }

            OutlinedButton(
                onClick = {
                    val cmd = if (phase == WalkPhase.PAUSED) MockLocationService.ACTION_RESUME_ROUTE
                    else MockLocationService.ACTION_PAUSE_ROUTE
                    context.startService(MockLocationService.command(context, cmd))
                },
                enabled = active && phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text(if (active && phase == WalkPhase.PAUSED) "繼續" else "暫停", fontSize = 12.sp) }

            OutlinedButton(
                onClick = {
                    context.startService(MockLocationService.command(context, MockLocationService.ACTION_STOP_ROUTE))
                    onMessage("已停止行走，停在目前模擬位置")
                },
                enabled = active && phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text("停止", fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onUndo, enabled = stops.isNotEmpty()) { Text("上一步 · 重選") }
            TextButton(onClick = {
                if (active) {
                    context.startService(MockLocationService.command(context, MockLocationService.ACTION_CLEAR_ROUTE))
                }
                onClear()
                onMessage("路點已清除，現有模擬位置不變")
            }, enabled = stops.isNotEmpty() || active) { Text("清除路點") }
        }
    }
}
