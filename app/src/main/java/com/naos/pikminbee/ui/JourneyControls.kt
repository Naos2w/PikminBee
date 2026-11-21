package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
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
    onSpeedSelected: (WalkSpeedPreset) -> Unit,
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
) {
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
        Text(if (flower) "尋花路點" else "道路路點",
            color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("${stops.size} 個路點" + if (origin == null) " · 尚未設定起點"
            else " · 起點 ${origin.label()}",
            color = muted, fontSize = 12.sp)
        if (stops.isNotEmpty()) {
            Text("順序： " + stops.indices.joinToString("  →  ") { "${it + 1}" },
                color = foreground, fontSize = 13.sp)
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
                    selected = speedPreset == option,
                    onClick = { onSpeedSelected(option) },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text("${option.label}\n${option.kilometresPerHour} km/h",
                            fontSize = 11.sp, lineHeight = 15.sp)
                    },
                )
            }
        }

        when {
            controlling -> Text(if (phase == WalkPhase.PAUSED) "已暫停" else "行走中",
                color = foreground, fontSize = 13.sp)
            otherWalking -> Text("先停止目前行走，才能規劃其他模式",
                color = muted, fontSize = 12.sp)
            routePlanning -> Text("正在查詢可步行道路…",
                color = muted, fontSize = 12.sp)
            hasPreview -> {
                val estimate = RouteEstimate.fromDistance(
                    WalkingGeometry.lengthMeters(preparedRoad), speedPreset)
                Text("預覽完成 · ${estimate.distanceLabel} · 約 ${estimate.durationLabel}",
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
            TextButton(
                onClick = onUndo,
                enabled = canEdit && stops.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "撤銷最後加入的路點" },
            ) {
                NaosActionIcon(NaosActionGlyph.UNDO, foreground)
                Text("撤銷上一點", color = foreground, fontSize = 12.sp)
            }
            TextButton(
                onClick = { onClear(); onMessage("所有路點已清除") },
                enabled = canEdit && stops.isNotEmpty(),
            ) { Text("清除全部", fontSize = 12.sp) }
        }
    }
}
