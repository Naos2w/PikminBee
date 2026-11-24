package com.naos.pikminbee.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.data.SavedPlace
import com.naos.pikminbee.data.SavedPlaceTimePolicy
import com.naos.pikminbee.data.SavedPlaceMetadataPolicy
import kotlinx.coroutines.delay

/**
 * Independent scrollable favourite sheet content, shared data with Android's
 * previous Saved tab. Selecting only previews a coordinate, never starts mock GPS.
 */
@Composable
fun FavouritesPanel(
    places: List<SavedPlace>,
    foreground: Color,
    muted: Color,
    onSelectPlace: (SavedPlace) -> Unit,
    onRenamePlace: (SavedPlace, String) -> Unit,
    onDeletePlace: (SavedPlace) -> Unit,
    backfillBusy: Boolean,
    backfillMessage: String,
    onBackfill: () -> Unit,
    routeContent: @Composable (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    var newName by remember { mutableStateOf("") }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val display = remember(places, search) {
        places.asSequence().filter { item ->
            search.isBlank() || listOfNotNull(item.name, item.city, item.countryName)
                .any { it.contains(search.trim(), ignoreCase = true) }
        }.sortedByDescending { it.createdAtMillis }.toList()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("收藏地點與路線", color = foreground, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp)
        if (places.any(SavedPlaceMetadataPolicy::incomplete)) {
            OutlinedButton(
                enabled = !backfillBusy,
                onClick = onBackfill,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (backfillBusy) "正在補齊收藏資訊…" else "補齊舊收藏地名／國旗／時區（每次最多 5 筆）") }
            Text("僅點擊後查詢 Photon／TimeAPI；不改動你的收藏名稱、座標或 GPS。",
                color = muted, fontSize = 11.sp)
        }
        if (backfillMessage.isNotBlank()) {
            Text(backfillMessage, color = muted, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it.take(80) },
            placeholder = { Text("搜尋地名、城市、國家或路線") },
            label = { Text("搜尋收藏") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "places-heading") {
                Text("收藏地點（${display.size}）", color = foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            if (display.isEmpty()) item(key = "places-empty") {
                Text(if (search.isEmpty()) "尚無收藏地點" else "沒有符合搜尋條件的地點",
                    color = muted, fontSize = 13.sp)
            }
            items(display, key = { "place-${it.id}" }) { place ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(LocalNaosVisualState.current.palette.soft,
                            RoundedCornerShape(14.dp))
                        .padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(place.name, color = foreground, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, maxLines = 1)
                    val context = LocalContext.current
                    val flag = remember(context, place.countryCode) {
                        SavedPlaceTimePolicy.flagAssetPath(place.countryCode)?.let { path ->
                            runCatching {
                                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                            }.getOrNull()
                        }
                    }
                    val countryLine = listOfNotNull(
                        place.countryName ?: place.countryCode, place.city,
                    ).joinToString(" · ")
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (flag != null) Image(
                            bitmap = flag,
                            contentDescription = null,
                            modifier = Modifier.size(width = 28.dp, height = 20.dp),
                        )
                        Text(countryLine.ifBlank { "國家／城市待確認，可補齊舊收藏" },
                            color = muted, fontSize = 12.sp)
                    }
                    val local = SavedPlaceTimePolicy.localClock(place.timeZoneId, nowMillis)
                    Text(if (local == null) "當地時間待確認，可補齊舊收藏"
                        else "當地時間 $local",
                        color = foreground, fontSize = 12.sp)
                    TextButton(onClick = {
                        detailId = if (detailId == place.id) null else place.id
                    }) { Text(if (detailId == place.id) "收起詳細資訊" else "詳細資訊", fontSize = 12.sp) }
                    if (detailId == place.id) {
                        Text("GPS：${place.coordinate.label()}", color = muted, fontSize = 11.sp)
                        Text("收藏 ${SavedPlaceTimePolicy.savedDate(place.createdAtMillis)}",
                            color = muted, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSelectPlace(place) },
                            modifier = Modifier.weight(1f)) {
                            NaosActionIcon(NaosActionGlyph.VIRTUAL_GPS, foreground)
                            Text("地圖選取", fontSize = 12.sp)
                        }
                        TextButton(onClick = { renaming = place; newName = place.name }) {
                            Text("改名", fontSize = 12.sp)
                        }
                        TextButton(onClick = { deleting = place }) {
                            Text("刪除", fontSize = 12.sp, color = Color(0xFFC45A4A))
                        }
                    }
                }
            }
            item(key = "routes") {
                Text("已收藏路線", color = foreground,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                routeContent(search.trim())
            }
        }
    }
    renaming?.let { place ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("修改收藏地名") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.take(80) },
                    label = { Text("地點名稱") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.trim().isNotEmpty(),
                    onClick = {
                        onRenamePlace(place, newName.trim())
                        renaming = null
                    },
                ) { Text("儲存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            },
        )
    }
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除收藏地點？") },
            text = { Text("將移除「${place.name}」，不會變更 GPS 或正在行走的路線。") },
            confirmButton = {
                TextButton(onClick = { onDeletePlace(place); deleting = null }) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}
