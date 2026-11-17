package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.flower.FlowerEvidence
import com.naos.pikminbee.flower.FlowerFinder
import com.naos.pikminbee.flower.FlowerSpot
import kotlin.math.roundToInt

/** All values are user-supplied observations, not live data from the Pikmin Bloom game. */
@Composable
fun FlowerCatalogPanel(
    spots: List<FlowerSpot>,
    origin: Coordinate,
    selected: Coordinate,
    draft: List<Coordinate>,
    foreground: Color,
    muted: Color,
    onRecord: (String, Coordinate) -> Unit,
    onMarkChecked: (FlowerSpot) -> Unit,
    onRemove: (FlowerSpot) -> Unit,
    onAddStop: (FlowerSpot) -> Unit,
    onAppendNearby: (List<FlowerSpot>) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(2000.0) }
    var onlyChecked by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<FlowerSpot?>(null) }
    val nearby = remember(spots, origin, radius, onlyChecked) {
        FlowerFinder.nearby(origin, spots, radius, onlyChecked, 100)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起花點資料庫" else "尋找附近花點（已記錄 ${spots.size}）")
        }
        if (!expanded) return@Column

        Text(
            "目前沒有官方即時大花資料。此清單來自你的記錄或自行匯入的資料，" +
                "匯入候選花點不是遊戲內已驗證的大花。",
            color = muted, fontSize = 12.sp,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            placeholder = { Text("花點名稱，例如：公園入口大花") },
            label = { Text("記錄目前地圖選點") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedButton(
            enabled = name.isNotBlank(),
            onClick = { onRecord(name.trim(), selected); name = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("記錄／確認此花點 · ${selected.label()}", fontSize = 12.sp) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text("匯入花點 JSON", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onExport, enabled = spots.isNotEmpty(),
                modifier = Modifier.weight(1f)) {
                Text("匯出 GeoJSON", fontSize = 12.sp)
            }
        }
        Text("附近搜尋以直線距離排序；實際行走距離仍由道路規劃決定。", color = muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(500.0, 2000.0, 5000.0).forEach { value ->
                FilterChip(selected = radius == value, onClick = { radius = value },
                    label = { Text(if (value < 1000) "500 m" else "${(value / 1000).toInt()} km", fontSize = 12.sp) })
            }
            FilterChip(selected = onlyChecked, onClick = { onlyChecked = !onlyChecked },
                label = { Text("僅本人確認", fontSize = 12.sp) })
        }
        Text("附近候選 ${nearby.size} 個", color = foreground, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            enabled = nearby.isNotEmpty() && draft.size < 14,
            onClick = { onAppendNearby(nearby.take(14)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("把附近花點加入尋花路線（最多 14 點）", fontSize = 12.sp) }

        if (nearby.isEmpty()) {
            Text("此範圍沒有已記錄的花點；可先手動標記或匯入候選資料。",
                color = muted, fontSize = 12.sp)
        } else {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nearby.take(25).forEach { spot ->
                    val distance = WalkingGeometry.distanceMeters(origin, spot.coordinate).roundToInt()
                    Column(Modifier.fillMaxWidth()) {
                        Text(spot.name, color = foreground, fontSize = 13.sp, maxLines = 1,
                            fontWeight = FontWeight.Medium)
                        Text("${distance} m · " + when (spot.evidence) {
                            FlowerEvidence.PERSONALLY_CHECKED -> "本人確認的花點"
                            FlowerEvidence.UNVERIFIED_IMPORT -> "匯入候選 · 未確認"
                        }, color = muted, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { onAddStop(spot) }, enabled = draft.size < 14) {
                                Text("加入路線", fontSize = 11.sp)
                            }
                            if (spot.evidence == FlowerEvidence.UNVERIFIED_IMPORT) {
                                TextButton(onClick = { onMarkChecked(spot) }) {
                                    Text("本人確認", fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = { pendingRemove = spot }) {
                                Text("移除", color = Color(0xFFC45A4A), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingRemove?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除花點？") },
            text = { Text("確定從本機花點資料庫移除「" + candidate.name + "」？尋花路線草稿不會自動更動。") },
            confirmButton = {
                TextButton(onClick = { onRemove(candidate); pendingRemove = null }) {
                    Text("確認移除", color = Color(0xFFC45A4A))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }
}
