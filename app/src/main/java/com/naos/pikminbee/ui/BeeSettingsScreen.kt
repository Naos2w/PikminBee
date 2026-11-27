package com.naos.pikminbee.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.naos.pikminbee.lan.LanStatus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import com.naos.pikminbee.data.InstalledAppVersion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.data.MapAppearance
import com.naos.pikminbee.data.MapBasemapPolicy
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.MockGpsJitterLevel
import com.naos.pikminbee.location.LocationCheckpoint

/** Common preferences live here, never repeated on each mode's map sheet. */
@Composable
fun BeeSettingsScreen(
    dark: Boolean,
    provider: MapProvider,
    appearance: MapAppearance,
    defaultSpeed: WalkSpeedProfile,
    virtualStrideMetres: Double,
    mockGpsJitterLevel: MockGpsJitterLevel,
    overlayVisible: Boolean,
    mockActive: Boolean,
    showWifiPanel: Boolean,
    checkpoint: LocationCheckpoint?,
    onProvider: (MapProvider) -> Unit,
    onAppearance: (MapAppearance) -> Unit,
    onDefaultSpeed: (WalkSpeedProfile) -> Unit,
    onVirtualStrideMetres: (Double) -> Unit,
    onMockGpsJitterLevel: (MockGpsJitterLevel) -> Unit,
    onPermissions: () -> Unit,
    overlayGuideEnabled: Boolean,
    overlayHoldHints: Boolean,
    onOverlayGuideEnabled: (Boolean) -> Unit,
    onOverlayHoldHints: (Boolean) -> Unit,
    onReplayOverlayGuide: () -> Unit,
    onOverlay: () -> Unit,
    onToggleWifi: () -> Unit,
    onAskLocation: () -> Unit,
    onMessage: (String) -> Unit,
    statusMessage: String,
    onBack: () -> Unit,
) {
    val palette = NaosDesignSystem.palette(NaosThemeFamily.MINERAL, dark)
    val foreground = palette.ink
    val muted = palette.muted
    val lan by LanStatus.display.collectAsState()
    var section by rememberSaveable { mutableStateOf<String?>(if (showWifiPanel) "wifi" else null) }
    LaunchedEffect(showWifiPanel) {
        if (showWifiPanel) {
            section = "wifi"
            onToggleWifi()
        }
    }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(defaultSpeed.mode == WalkSpeedMode.FIXED) }
    var customValue by rememberSaveable { mutableStateOf(WalkSpeedProfile.number(defaultSpeed.fixedKmh)) }
    LaunchedEffect(defaultSpeed) {
        customOpen = defaultSpeed.mode == WalkSpeedMode.FIXED
        if (customOpen) customValue = WalkSpeedProfile.number(defaultSpeed.fixedKmh)
    }
    val installed = InstalledAppVersion.read(LocalContext.current)
    val titles = mapOf("route" to "進階路徑設定",
        "gps" to "GPS 測試", "overlay" to "懸浮控制教學", "wifi" to "Wi-Fi 控制",
        "permission" to "權限", "health" to "步數與健康")
    BackHandler(enabled = section != null) { section = null }
    Column(
        Modifier.fillMaxSize().background(palette.bg)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (section != null) section = null else onBack() },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "返回" }) {
                Text("‹", fontSize = 27.sp, color = foreground)
            }
            Text(section?.let { titles[it] } ?: "設定", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = foreground, modifier = Modifier.weight(1f))
            IconButton(onClick = { helpOpen = true },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "設定說明" }) {
                Text("?", fontSize = 21.sp, color = foreground)
            }
        }
        if (section == null) {
            Surface(modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = palette.surface) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("介面外觀", fontWeight = FontWeight.SemiBold, color = foreground)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MapAppearance.SYSTEM to "系統", MapAppearance.LIGHT to "淺色",
                            MapAppearance.DARK to "深色").forEach { (option, label) ->
                            FilterChip(selected = appearance == option, onClick = { onAppearance(option) },
                                label = { Text(label) })
                        }
                    }
                    if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
                        Text("地圖來源", fontSize = 12.sp, color = muted)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MapBasemapPolicy.availableProviders(true).forEach { option ->
                                FilterChip(selected = provider == option, onClick = { onProvider(option) },
                                    label = { Text(if (option == MapProvider.OPEN_FREE_MAP) "OpenFreeMap" else "Google Maps") })
                            }
                        }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = palette.surface) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("預設速度", fontWeight = FontWeight.SemiBold, color = foreground)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WalkSpeedPreset.entries.forEach { option ->
                            FilterChip(selected = !customOpen && defaultSpeed.mode == WalkSpeedMode.PRESET &&
                                defaultSpeed.preset == option, onClick = {
                                    customOpen = false
                                    onDefaultSpeed(WalkSpeedProfile.preset(option))
                                }, label = {
                                    Text(option.label + "\n" + WalkSpeedProfile.number(option.kilometresPerHour),
                                        fontSize = 11.sp, lineHeight = 15.sp)
                                })
                        }
                        FilterChip(selected = customOpen, onClick = { customOpen = true },
                            label = { Text("自訂", fontSize = 11.sp) })
                    }
                    if (customOpen) {
                        val valid = WalkSpeedProfile.tryFixed(customValue)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = customValue, onValueChange = { customValue = it },
                                label = { Text("km/h") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = valid == null, modifier = Modifier.weight(1f))
                            TextButton(enabled = valid != null, onClick = {
                                valid?.let { onDefaultSpeed(it) }
                            }) { Text("儲存") }
                        }
                        if (valid == null) Text("請輸入有效速度", color = palette.muted, fontSize = 12.sp)
                    }
                    Text("下次啟動 App：${defaultSpeed.display}", color = muted, fontSize = 12.sp)
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = palette.surface) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("懸浮控制", modifier = Modifier.weight(1f), color = foreground)
                        Switch(checked = overlayVisible, onCheckedChange = { onOverlay() })
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("長按圖示提示", modifier = Modifier.weight(1f), color = foreground)
                        Switch(checked = overlayHoldHints, onCheckedChange = onOverlayHoldHints)
                    }
                    if (statusMessage.contains("懸浮") || statusMessage.contains("授權")) {
                        Text(statusMessage, color = muted, fontSize = 12.sp)
                    }
                }
            }
            val rows = listOf(
                Triple("route", NaosActionGlyph.SPEED, "步幅"),
                Triple("gps", NaosActionGlyph.REAL_GPS, if (mockActive) "模擬定位中" else "真實定位"),
                Triple("overlay", NaosActionGlyph.MORE, "教學"),
                Triple("wifi", NaosActionGlyph.WIFI, lan.connection.label),
                Triple("permission", NaosActionGlyph.STEPS, ""),
                Triple("health", NaosActionGlyph.STEPS, "Health Connect"),
            )
            rows.forEach { (id, glyph, value) ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { section = id },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = palette.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NaosActionIcon(glyph, foreground)
                        Text(titles.getValue(id), color = foreground, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        if (value.isNotBlank()) Text(value, color = muted, fontSize = 11.sp,
                            maxLines = 2)
                        Text("›", color = muted, fontSize = 23.sp)
                    }
                }
            }
            SelectionContainer {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(installed.displayName, color = muted, fontSize = 12.sp)
                    Text("版本碼：${installed.code?.toString() ?: "待確認"}", color = muted, fontSize = 10.sp)
                }
            }
        } else if (section == "health") {
            HealthConnectReadSettings(palette = palette)
        } else Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = palette.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (section) {
                    "route" -> {
                VirtualStrideEditor(
                    strideMetres = virtualStrideMetres,
                    onApply = onVirtualStrideMetres,
                    onMessage = onMessage,
                    muted = muted,
                )

                    }
                    "gps" -> {
                Text("模擬定位微幅漂移 · 測試用",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = foreground)
                Text("只讓模擬 GPS 回報座標有小幅波動，不改路線、步數或真實 GPS；預設關閉。",
                    fontSize = 12.sp, color = muted)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MockGpsJitterLevel.entries.forEach { option ->
                        val label = when (option) {
                            MockGpsJitterLevel.OFF -> "關閉"
                            MockGpsJitterLevel.LOW -> "0.5 公尺"
                            MockGpsJitterLevel.MEDIUM -> "1.0 公尺"
                            MockGpsJitterLevel.HIGH -> "1.5 公尺"
                        }
                        FilterChip(
                            selected = mockGpsJitterLevel == option,
                            onClick = { onMockGpsJitterLevel(option) },
                            label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }

                    }
                    "overlay" -> {
                Text("懸浮控制教學", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("首次開啟顯示教學", color = foreground, fontSize = 14.sp)
                    }
                    Switch(checked = overlayGuideEnabled, onCheckedChange = onOverlayGuideEnabled)
                }
                TextButton(onClick = onReplayOverlayGuide) {
                    Text("查看懸浮控制操作說明")
                }

                    }
                    "wifi" -> WifiControlPanel(isMockActive = mockActive,
                        foreground = foreground, muted = muted, onMessage = onMessage)
                    "permission" -> {
                        TextButton(onClick = onPermissions) { Text("檢查 Android 權限與模擬位置") }
                BackgroundSessionPanel(
                    isMockActive = mockActive,
                    checkpoint = checkpoint,
                    foreground = foreground,
                    muted = muted,
                    onMessage = onMessage,
                    onAskLocationPermission = onAskLocation,
                )

                    }
                    "about" -> SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(installed.displayName, color = foreground, fontSize = 16.sp)
                            Text("版本碼：${installed.code?.toString() ?: "待確認"}", color = muted, fontSize = 12.sp)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
    if (helpOpen) AlertDialog(
        onDismissRequest = { helpOpen = false },
        title = { Text("設定說明") },
        text = { Text(when (section) {
            "map" -> "選擇地圖與介面主題，設定保存在手機。"
            "route" -> "設定預設速度及估算步幅；路徑仍須先預覽才能開始。"
            "gps" -> "僅供授權測試；不改變真實 GPS 或既有路徑。"
            "overlay" -> "懸浮只提供快捷控制；關閉視窗不停止路徑或 Wi-Fi。"
            "wifi" -> "只在可信私人 Wi-Fi 開啟，電腦輸入配對碼後須由手機核准。HTTP 未加密，勿公開連接埠。"
            "permission" -> "Android 定位與背景權限需由你明確授權，可隨時撤銷。"
            "health" -> "今日步數讀取、實際感測器計步、真實步數同步及懸浮統計可獨立管理。路線估算可獨立選用分段同步至 Health Connect（手動估算紀錄），健康資料不傳送至 Web。"
            else -> "點分類查看及修改相關設定；返回可回到設定首頁。"
        }) },
        confirmButton = { TextButton(onClick = { helpOpen = false }) { Text("關閉") } },
    )
}
