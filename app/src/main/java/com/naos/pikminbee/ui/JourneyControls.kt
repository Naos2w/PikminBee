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
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val flower = mode == WalkMode.FLOWERS
    val active = activeMode == mode
    var loop by remember { mutableStateOf(flower) }
    var planning by remember { mutableStateOf(false) }
    var requestRevision by remember { mutableIntStateOf(0) }
    var previewDistance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(stops, origin, loop, mode) {
        requestRevision++
        previewDistance = null
        if (flower) onPreviewRoad(emptyList())
    }
    fun requestedPoints(start: Coordinate): List<Coordinate> =
        if (flower) FlowerMapDraft.routePoints(start, stops, loop) else buildList {
            add(start)
            stops.forEach { stop ->
                if (WalkingGeometry.distanceMeters(last(), stop) > 1.0) add(stop)
            }
        }

    fun previewOrStart(startWalking: Boolean) {
        if (planning) return
        val start = origin ?: run {
            onMessage("請先在地圖選位置，並設為尋花起點")
            return
        }
        if (startWalking && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onAskLocation()
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
        onMessage("正在查詢步行道路，失敗不會改成直線行走")
        coroutineScope.launch {
            try {
                val geometry = ValhallaPlanner.plan(points)
                if (revision != requestRevision) return@launch
                if (flower) {
                    onPreviewRoad(geometry)
                    previewDistance = WalkingGeometry.lengthMeters(geometry)
                }
                if (startWalking) {
                    ContextCompat.startForegroundService(
                        context, MockLocationService.routeIntent(
                            context, geometry, WalkSpeedState.selected.value.metresPerSecond,
                            mode, loop = flower && loop
                        ),
                    )
                    onMessage("道路路線已取得，開始移動（${geometry.size} 個節點）")
                } else {
                    onMessage("已在地圖預覽步行道路，尚未開始移動")
                }
            } catch (e: Exception) {
                onMessage(e.message ?: "規劃步行道路失敗")
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
            else "在地圖依序點選目的地，開始後才查詢行人道路，避免浪費路線服務。",
            color = muted, fontSize = 12.sp
        )
        Text("已加入 ${stops.size} 個路點 · 起點 ${origin?.label() ?: "尚未設定"}",
            color = muted, fontSize = 12.sp)
        Text("本次行走速度（個人預設請至共用設定）", color = muted, fontSize = 12.sp)
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
            onClick = { loop = !loop },
            label = { Text("循環路線", fontSize = 12.sp) },
        )

        if (flower) {
            OutlinedButton(
                onClick = { previewOrStart(startWalking = false) },
                enabled = !planning && stops.isNotEmpty() && origin != null &&
                    (!active || phase !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text(if (planning) "道路規劃中…" else "先預覽步行道路（不移動）", fontSize = 12.sp) }
            previewDistance?.let { metres ->
                Text(
                    "預覽步行路線約 " + "%.0f".format(java.util.Locale.US, metres) +
                        " 公尺 · 目前未因此開始行走",
                    color = muted, fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { previewOrStart(startWalking = true) },
                enabled = !planning && stops.isNotEmpty() && origin != null &&
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
            TextButton(onClick = onUndo, enabled = stops.isNotEmpty()) { Text("撤銷上一點") }
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
