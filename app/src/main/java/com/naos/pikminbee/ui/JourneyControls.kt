package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.RouteEstimate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.domain.WalkingGeometry

/**
 * Main-app editor for both types of walk.
 *
 * The map quick bar and this sheet call the same preview/confirm/playback
 * handler. Opening the sheet can never silently start a new GPS route.
 */
@Composable
fun JourneyControls(
    mode: WalkMode,
    stops: List<Coordinate>,
    origin: Coordinate?,
    activeMode: WalkMode?,
    phase: WalkPhase,
    walkEstimatedSteps: Long,
    speedPreset: WalkSpeedPreset,
    speedProfile: WalkSpeedProfile,
    onSpeedSelected: (WalkSpeedProfile) -> Unit,
    foreground: Color,
    muted: Color,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onMessage: (String) -> Unit,
    preparedRoad: List<Coordinate>,
    routePlanning: Boolean,
    snapMetres: Double,
    loop: Boolean,
    onLoopChange: (Boolean) -> Unit,
    onRoutePrimary: () -> Unit,
    onRouteStop: () -> Unit,
    onLocateStop: (Coordinate) -> Unit = {},
    onEditStop: (Int) -> Unit = {},
    onMoveStop: (Int, Int) -> Unit = { _, _ -> },
    onDeleteStop: (Int) -> Unit = {},
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val activeWalk = phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED
    val controlling = activeWalk && activeMode == mode
    val otherWalking = activeWalk && activeMode != mode
    val flower = mode == WalkMode.FLOWERS
    val hasPreview = preparedRoad.size >= 2
    val canPlan = origin != null && stops.isNotEmpty()
    val canEdit = !activeWalk && !routePlanning
    val primaryLabel = when {
        controlling && phase == WalkPhase.PAUSED -> "繼續"
        controlling -> "暫停"
        otherWalking -> "其他行走進行中"
        routePlanning -> "規劃中…"
        hasPreview -> "開始"
        else -> "規劃"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("路徑 · ${stops.size} 個路點",
            color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("起點  " + (origin?.label() ?: "尚未設定"), color = muted,
                fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (origin != null) IconButton(onClick = { onLocateStop(origin) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "查看起點" }) {
                NaosActionIcon(NaosActionGlyph.REAL_GPS, foreground)
            }
        }
        stops.forEachIndexed { index, point ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val colour = when (index) {
                    0 -> Color(0xFF159E5E)
                    stops.lastIndex -> Color(0xFFD35C51)
                    else -> Color(0xFF306EEE)
                }
                Box(Modifier.size(29.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) {
                    Text((index + 1).toString(), fontSize = 12.sp, color = Color.White,
                        fontWeight = FontWeight.Bold)
                }
                Text(point.label(), fontSize = 12.sp, color = foreground,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { onLocateStop(point) },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "查看第 ${index + 1} 點" }) {
                    NaosActionIcon(NaosActionGlyph.REAL_GPS, foreground)
                }
                if (canEdit) Box {
                    IconButton(onClick = { menuIndex = index },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "第 ${index + 1} 點更多操作" }) {
                        NaosActionIcon(NaosActionGlyph.MORE, muted)
                    }
                    DropdownMenu(expanded = menuIndex == index, onDismissRequest = { menuIndex = null }) {
                        DropdownMenuItem(text = { Text("上移") }, enabled = index > 0,
                            onClick = { menuIndex = null; onMoveStop(index, -1) })
                        DropdownMenuItem(text = { Text("下移") }, enabled = index < stops.lastIndex,
                            onClick = { menuIndex = null; onMoveStop(index, 1) })
                        DropdownMenuItem(text = { Text("重新選點") },
                            onClick = { menuIndex = null; onEditStop(index) })
                        DropdownMenuItem(text = { Text("刪除") },
                            onClick = { menuIndex = null; onDeleteStop(index) })
                    }
                }
            }
        }
        if (flower) {
            FilterChip(
                selected = loop,
                onClick = { onLoopChange(!loop) },
                enabled = canEdit,
                label = { Text("循環路線") },
            )
        }
        Text("速度", color = muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WalkSpeedPreset.entries.forEach { option ->
                FilterChip(
                    selected = speedProfile.mode == WalkSpeedMode.PRESET &&
                        speedPreset == option,
                    onClick = { onSpeedSelected(WalkSpeedProfile.preset(option)) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text("${option.label}\n${option.kilometresPerHour} km/h",
                            fontSize = 11.sp, lineHeight = 15.sp)
                    },
                )
            }
        }
        WalkSpeedAdvancedControls(
            profile = speedProfile,
            onSelected = onSpeedSelected,
            onMessage = onMessage,
            foreground = foreground,
            muted = muted,
        )

        when {
            controlling -> Text(if (phase == WalkPhase.PAUSED) "已暫停" else "行走中",
                color = foreground, fontSize = 13.sp)
            otherWalking -> Text("先停止目前行走，才能規劃其他模式",
                color = muted, fontSize = 12.sp)
            routePlanning -> Text("正在查詢可步行道路…",
                color = muted, fontSize = 12.sp)
            hasPreview -> {
                val estimate = RouteEstimate.fromDistanceAtMps(
                    WalkingGeometry.lengthMeters(preparedRoad), speedProfile.nominalMps)
                Text("預覽完成 · ${estimate.distanceLabel} · 約 ${estimate.durationLabel}" +
                    if (speedProfile.mode == WalkSpeedMode.RANGE) "（區間均速估計）" else "",
                    color = foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (snapMetres > 40.0) {
                    Text("注意：路點最多偏離可步行道路約 ${snapMetres.toInt()} 公尺，開始前須確認",
                        color = Color(0xFFD59C36), fontSize = 12.sp)
                }
            }
            else -> Text("先選路點並預覽，不會自動移動定位。",
                color = muted, fontSize = 12.sp)
        }
        if (controlling || (phase == WalkPhase.PARKED &&
                activeMode == mode && walkEstimatedSteps > 0L)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NaosActionIcon(NaosActionGlyph.STEPS, foreground)
                Text("本次估算 ${walkEstimatedSteps} 步",
                    color = foreground, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRoutePrimary,
                enabled = controlling || (!otherWalking && !routePlanning && canPlan),
                modifier = Modifier.weight(1.5f).height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (controlling && phase == WalkPhase.MOVING)
                        Color(BeeActionColors.PAUSE_AMBER)
                    else if (hasPreview || controlling) Color(BeeActionColors.START_GREEN)
                    else Color(0xFF567A86),
                    contentColor = Color.White,
                    disabledContainerColor = muted.copy(alpha = .14f),
                    disabledContentColor = muted,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    NaosActionIcon(when {
                        controlling && phase == WalkPhase.MOVING -> NaosActionGlyph.PAUSE
                        controlling || hasPreview -> NaosActionGlyph.PLAY
                        else -> NaosActionGlyph.PLAN
                    }, Color.White)
                    Text(primaryLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onRouteStop,
                enabled = controlling,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(BeeActionColors.STOP_RED),
                    contentColor = Color.White,
                    disabledContainerColor = muted.copy(alpha = .14f),
                    disabledContentColor = muted,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    NaosActionIcon(NaosActionGlyph.STOP, Color.White)
                    Text("停止", fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onUndo, enabled = canEdit && stops.isNotEmpty(),
                modifier = Modifier.size(48.dp).semantics { contentDescription = "撤銷最後加入的路點" }) {
                NaosActionIcon(NaosActionGlyph.UNDO, foreground)
            }
            TextButton(onClick = { confirmClear = true }, enabled = canEdit && stops.isNotEmpty()) {
                Text("清除全部", fontSize = 12.sp)
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清除全部路點？") },
        text = { Text("不會移動目前位置。") },
        confirmButton = { TextButton(enabled = canEdit, onClick = {
            confirmClear = false; onClear(); onMessage("所有路點已清除")
        }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}
