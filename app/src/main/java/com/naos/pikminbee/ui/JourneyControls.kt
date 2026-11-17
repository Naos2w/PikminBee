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
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
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
    origin: Coordinate,
    activeMode: WalkMode?,
    phase: WalkPhase,
    foreground: Color,
    muted: Color,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onMessage: (String) -> Unit,
    onAskLocation: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val flower = mode == WalkMode.FLOWERS
    val active = activeMode == mode
    var speed by remember { mutableStateOf(1.4) }
    var loop by remember { mutableStateOf(flower) }
    var planning by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (flower) "尋花巡迴" else "多點道路路徑",
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
        Text(
            if (flower) "在地圖依序標記花點，沿步行道路巡迴。遊戲大花自動發現尚待資料驗證。"
            else "在地圖依序點選目的地，開始後才查詢行人道路，避免浪費路線服務。",
            color = muted, fontSize = 12.sp
        )
        Text("已加入 ${stops.size} 個路點 · 起點 ${origin.label()}",
            color = muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(1.0, 1.4, 2.0).forEach { s ->
                FilterChip(
                    selected = speed == s,
                    onClick = { speed = s },
                    label = { Text("${s} m/s", fontSize = 12.sp) }
                )
            }
            if (flower) FilterChip(
                selected = loop,
                onClick = { loop = !loop },
                label = { Text("循環", fontSize = 12.sp) }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                        onAskLocation()
                    } else {
                        val points = buildList {
                            add(origin)
                            stops.forEach { point ->
                                if (WalkingGeometry.distanceMeters(last(), point) > 1.0) add(point)
                            }
                            if (flower && loop && WalkingGeometry.distanceMeters(last(), origin) > 1.0) add(origin)
                        }
                        if (points.size !in 2..16) {
                            onMessage("請至少選取一個不同地點；循環模式最多 14 個花點")
                        } else {
                            planning = true
                            onMessage("正在查詢步行道路，路線失敗不會改成直線行走")
                            coroutineScope.launch {
                                try {
                                    val geometry = ValhallaPlanner.plan(points)
                                    ContextCompat.startForegroundService(
                                        context, MockLocationService.routeIntent(context, geometry, speed, mode,
                                            loop = flower && loop)
                                    )
                                    onMessage("已取得步行道路，開始移動（${geometry.size} 個節點）")
                                } catch (e: Exception) {
                                    onMessage(e.message ?: "規劃步行道路失敗")
                                } finally {
                                    planning = false
                                }
                            }
                        }
                    }
                },
                enabled = !planning && stops.isNotEmpty(),
                modifier = Modifier.weight(1.3f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9F5E0C)),
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
