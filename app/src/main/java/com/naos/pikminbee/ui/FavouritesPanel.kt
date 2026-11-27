package com.naos.pikminbee.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.data.SavedPlace
import com.naos.pikminbee.data.SavedPlaceTimePolicy
import com.naos.pikminbee.data.SavedPlaceMetadataPolicy
import kotlinx.coroutines.delay

/** Compact two-line place cards; selection never starts mock GPS. */
@Composable
fun FavouritesPanel(
    places: List<SavedPlace>,
    routeCount: Int,
    foreground: Color,
    muted: Color,
    onSelectPlace: (SavedPlace) -> Unit,
    onRenamePlace: (SavedPlace, String) -> Unit,
    onDeletePlace: (SavedPlace) -> Unit,
    canAddPlace: Boolean,
    savingPlace: Boolean,
    onAddPlace: () -> Unit,
    initialRoutes: Boolean = false,
    canAddRoute: Boolean = false,
    onAddRoute: () -> Unit = {},
    backfillBusy: Boolean,
    backfillMessage: String,
    onBackfill: () -> Unit,
    routeContent: @Composable (String) -> Unit,
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var showingRoutes by remember { mutableStateOf(initialRoutes) }
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    var newName by remember { mutableStateOf("") }
    var menuId by remember { mutableStateOf<Long?>(null) }
    var helpOpen by remember { mutableStateOf(false) }
    var headerMenuOpen by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            // Align updates to the actual minute boundary rather than opening time.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val display = remember(places, search) {
        places.asSequence().filter { item ->
            search.isBlank() || listOfNotNull(item.name, item.city, item.countryName, item.countryCode)
                .any { it.contains(search.trim(), ignoreCase = true) }
        }.sortedByDescending { it.createdAtMillis }.toList()
    }
    val hasIncomplete = places.any(SavedPlaceMetadataPolicy::incomplete)
    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("收藏地點與路線", color = foreground, fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = if (showingRoutes) onAddRoute else onAddPlace,
                enabled = if (showingRoutes) canAddRoute else canAddPlace && !savingPlace,
                modifier = Modifier.semantics { contentDescription = if (showingRoutes) "收藏目前路線" else "收藏目前選取的位置" }) {
                Text(if (!showingRoutes && savingPlace) "…" else "+", fontSize = 25.sp, color = foreground)
            }
            Box {
                IconButton(onClick = { headerMenuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "收藏說明與更多操作" }) {
                    Text("?", fontSize = 21.sp, color = foreground)
                }
                DropdownMenu(expanded = headerMenuOpen, onDismissRequest = { headerMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("收藏操作說明") }, onClick = {
                        headerMenuOpen = false; helpOpen = true
                    })
                    if (hasIncomplete) DropdownMenuItem(
                        text = { Text("補齊舊收藏資料") }, enabled = !backfillBusy, onClick = {
                            headerMenuOpen = false; onBackfill()
                        })
                }
            }
        }
        if (backfillBusy || backfillMessage.isNotBlank()) {
            Text(if (backfillBusy) "正在補齊收藏資料…" else backfillMessage, color = muted, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it.take(80) },
            placeholder = { Text("搜尋地名、城市、國家或路線") },
            label = { Text("搜尋收藏") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showingRoutes) {
                OutlinedButton(onClick = { showingRoutes = false }, modifier = Modifier.weight(1f)) {
                    Text("地點（${display.size}）")
                }
                Button(onClick = { showingRoutes = true }, modifier = Modifier.weight(1f)) {
                    Text("路線（$routeCount）")
                }
            } else {
                Button(onClick = { showingRoutes = false }, modifier = Modifier.weight(1f)) {
                    Text("地點（${display.size}）")
                }
                OutlinedButton(onClick = { showingRoutes = true }, modifier = Modifier.weight(1f)) {
                    Text("路線（$routeCount）")
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showingRoutes) {
                item(key = "routes") { routeContent(search.trim()) }
            } else {
                if (display.isEmpty()) item(key = "places-empty") {
                    Text(if (search.isEmpty()) "尚無收藏地點" else "沒有符合搜尋條件的地點",
                        color = muted, fontSize = 13.sp)
                }
                items(display, key = { "place-${it.id}" }) { place ->
                    val flag = remember(context, place.countryCode) {
                        SavedPlaceTimePolicy.flagAssetPath(place.countryCode)?.let { path ->
                            runCatching {
                                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                            }.getOrNull()
                        }
                    }
                    val local = SavedPlaceTimePolicy.localClock(place.timeZoneId, nowMillis, place.countryCode)
                    val dateTime = local ?: "—/— —:—"
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(LocalNaosVisualState.current.palette.soft)
                            .clickable { onSelectPlace(place) }
                            .padding(start = 11.dp, end = 5.dp, top = 10.dp, bottom = 2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (flag != null) Image(
                                bitmap = flag,
                                contentDescription = place.countryName ?: place.countryCode,
                                modifier = Modifier.size(width = 28.dp, height = 20.dp),
                            ) else Text("—", color = muted, fontSize = 12.sp)
                            Text(place.name, color = foreground, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).semantics { contentDescription = place.name })
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                NaosActionIcon(NaosActionGlyph.CLOCK, muted, Modifier.size(14.dp))
                                Text(dateTime, color = foreground, fontSize = 12.sp,
                                    modifier = Modifier.semantics { contentDescription =
                                        if (local == null) "當地日期時間未知" else "當地日期時間 $dateTime" })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(place.coordinate.label(), color = muted, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { menuId = place.id },
                                    modifier = Modifier.semantics {
                                        contentDescription = "${place.name} 的更多操作"
                                    }) {
                                    NaosActionIcon(NaosActionGlyph.MORE, muted)
                                }
                                DropdownMenu(expanded = menuId == place.id, onDismissRequest = { menuId = null }) {
                                    DropdownMenuItem(text = { Text("改名") }, onClick = {
                                        menuId = null; renaming = place; newName = place.name
                                    })
                                    DropdownMenuItem(text = { Text("複製座標") }, onClick = {
                                        menuId = null
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard.setPrimaryClip(ClipData.newPlainText("PikminBee 座標", place.coordinate.label()))
                                        Toast.makeText(context, "已複製座標", Toast.LENGTH_SHORT).show()
                                    })
                                    if (SavedPlaceMetadataPolicy.incomplete(place)) DropdownMenuItem(
                                        text = { Text("補齊舊收藏資料") }, enabled = !backfillBusy, onClick = {
                                            menuId = null; onBackfill()
                                        })
                                    DropdownMenuItem(text = { Text("刪除", color = Color(0xFFC45A4A)) }, onClick = {
                                        menuId = null; deleting = place
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (helpOpen) AlertDialog(
        onDismissRequest = { helpOpen = false },
        title = { Text("收藏操作說明") },
        text = { Text("點卡片只會選取地圖；⋯ 可改名、複製座標、刪除或補齊資料。當地日期時間依收藏地點時區，每分鐘更新；未知時區顯示 —/— —:—。僅明確收藏或補齊時才查詢地標與時區。") },
        confirmButton = { TextButton(onClick = { helpOpen = false }) { Text("關閉") } },
    )
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
