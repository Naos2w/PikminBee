package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode

/** Route favourites are distinct from single-location favourites and live walking state. */
@Composable
fun RouteLibraryPanel(
    mode: WalkMode,
    draft: List<Coordinate>,
    routes: List<SavedRoute>,
    foreground: Color,
    muted: Color,
    onSave: (String) -> Boolean,
    onRename: (SavedRoute, String) -> Boolean,
    onLoad: (SavedRoute) -> Unit,
    onExport: (SavedRoute) -> Unit,
    onDelete: (SavedRoute) -> Unit,
    onImport: () -> Unit,
) {
    var name by remember(mode) { mutableStateOf("") }
    var editing by remember(mode) { mutableStateOf<SavedRoute?>(null) }
    var expanded by remember(mode) { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SavedRoute?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起已收藏路線" else "收藏路線／GPX（" + routes.size + "）")
        }
        if (expanded) {
            Text(
                "儲存目前的路點，下次可直接載入。舊巡花收藏也會以一般路線草稿開啟，不會自動開始行走。",
                color = muted, fontSize = 12.sp,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) },
                label = { Text(if (editing == null) "路線名稱" else "修改路線名稱") },
                placeholder = { Text("例如：台北公園步行") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(
                    enabled = name.isNotBlank() && (editing != null || draft.isNotEmpty()),
                    onClick = {
                        val selected = editing
                        val saved = if (selected == null) onSave(name.trim())
                        else onRename(selected, name.trim())
                        if (saved) {
                            name = ""
                            editing = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (editing == null) "收藏這條路線" else "確認改名", fontSize = 12.sp) }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("匯入 GPX", fontSize = 12.sp)
                }
            }
            if (editing != null) {
                TextButton(onClick = { editing = null; name = "" }) { Text("取消改名") }
            }
            if (routes.isEmpty()) {
                Text("尚未收藏任何路線。", color = muted, fontSize = 12.sp)
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    routes.forEach { route ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(route.name, color = foreground, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(route.stops.size.toString() + " 個路點 · " +
                                (if (route.mode == WalkMode.FLOWERS) "舊巡花收藏 · 可轉一般路線" else "道路"),
                                color = muted, fontSize = 11.sp)
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { onLoad(route) }) {
                                    Text("載入", fontSize = 12.sp)
                                }
                                TextButton(onClick = { onExport(route) }) {
                                    Text("GPX", fontSize = 12.sp)
                                }
                                TextButton(onClick = { editing = route; name = route.name }) {
                                    Text("改名", fontSize = 12.sp)
                                }
                                TextButton(onClick = { deleteCandidate = route }) {
                                    Text("刪除", fontSize = 12.sp, color = Color(0xFFC54F4F))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("刪除收藏路線？") },
            text = { Text("「" + candidate.name + "」將從本機收藏移除；目前草稿與正在執行的定位不會變動。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(candidate)
                    deleteCandidate = null
                    if (editing?.id == candidate.id) {
                        editing = null
                        name = ""
                    }
                }) { Text("確認刪除", color = Color(0xFFC54F4F)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
            },
        )
    }
}
