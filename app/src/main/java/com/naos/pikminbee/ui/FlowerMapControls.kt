package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.flower.FlowerEvidence
import com.naos.pikminbee.flower.FlowerSpot

/** Selected map coordinate and optional previously recorded user data. */
data class FlowerMapPick(val coordinate: Coordinate, val flower: FlowerSpot? = null)

/** A single map-first task: select -> confirm -> order stops -> preview/start. */
@Composable
fun FlowerMapControls(
    start: Coordinate?,
    usingMockStart: Boolean,
    selected: FlowerMapPick?,
    editingStopIndex: Int?,
    stops: List<Coordinate>,
    canEdit: Boolean,
    foreground: Color,
    muted: Color,
    onAddSelected: () -> Unit,
    onReplaceStop: () -> Unit,
    onSelectStop: (Int) -> Unit,
    onCancelStopEdit: () -> Unit,
    onUseAsStart: () -> Unit,
    onDismissSelected: () -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("地圖選花 · 設定行走路線", fontWeight = FontWeight.Bold, color = foreground)
        Text(if (canEdit) "選位置 → 加入路點 → 規劃道路 → 開始"
            else "行走中無法編輯路點，請先停止",
            color = muted, fontSize = 12.sp)
        Text(
            when {
                usingMockStart && start != null -> "起點：目前模擬位置 ${start.label()}"
                start != null -> "起點：地圖指定 ${start.label()}"
                else -> "尚未設定起點：先點地圖位置，再按「設為起點」"
            },
            color = foreground, fontSize = 12.sp,
        )
        val editing = editingStopIndex?.takeIf { it in stops.indices }
        if (editing != null) {
            Text("正在編輯第 ${editing + 1} 個路點", color = foreground,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("點地圖選新位置，再確認替換。",
                color = muted, fontSize = 12.sp)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onCancelStopEdit) { Text("取消路點編輯", fontSize = 12.sp) }
                TextButton(onClick = { onRemoveStop(editing) }, enabled = canEdit) {
                    Text("移除此路點", color = Color(0xFFC45A4A), fontSize = 12.sp)
                }
            }
        }
        if (selected != null) {
            Text(
                selected.flower?.let {
                    it.name + " · " + if (it.evidence == FlowerEvidence.PERSONALLY_CHECKED)
                        "本人已記錄" else "匯入候選、尚未確認"
                } ?: "自選地圖位置（尚未記錄為花點）",
                color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
            Text(selected.coordinate.label(), color = muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = if (editing != null) onReplaceStop else onAddSelected,
                    enabled = canEdit && start != null && (editing != null || stops.size < 14),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9F5E0C),
                        contentColor = Color.White,
                    ),
                ) { Text(if (editing != null) "替換第 ${editing + 1} 點" else "加入尋花路線", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onUseAsStart, enabled = canEdit && !usingMockStart && editing == null,
                    modifier = Modifier.weight(1f),
                ) { Text("設為起點", fontSize = 12.sp) }
            }
            TextButton(onClick = onDismissSelected) { Text("取消選取", fontSize = 12.sp) }
        }
        if (editing != null && selected == null) {
            Text("請點地圖選新位置，也可以先取消編輯。", color = muted, fontSize = 12.sp)
        }
        if (stops.isNotEmpty()) {
            Text("路點順序（${stops.size}/14）", color = foreground,
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            stops.forEachIndexed { index, coordinate ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { onSelectStop(index) },
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "${index + 1}. ${coordinate.label()}" +
                                if (editing == index) " · 編輯中" else "",
                            color = if (editing == index) foreground else muted,
                            fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(enabled = canEdit && index > 0, onClick = { onMoveStop(index, -1) },
                        modifier = Modifier.semantics { contentDescription = "第 ${index + 1} 點上移" }) {
                        Text("↑", fontSize = 14.sp)
                    }
                    TextButton(enabled = canEdit && index < stops.lastIndex,
                        onClick = { onMoveStop(index, 1) },
                        modifier = Modifier.semantics { contentDescription = "第 ${index + 1} 點下移" }) { Text("↓", fontSize = 14.sp) }
                    TextButton(onClick = { onRemoveStop(index) }, enabled = canEdit) {
                        Text("移除", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
