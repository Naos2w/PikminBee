package com.naos.pikminbee.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.data.FreeMapStyle
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
    freeMapStyle: FreeMapStyle,
    appearance: MapAppearance,
    defaultSpeed: WalkSpeedPreset,
    virtualStrideMetres: Double,
    mockGpsJitterLevel: MockGpsJitterLevel,
    overlayVisible: Boolean,
    mockActive: Boolean,
    showWifiPanel: Boolean,
    checkpoint: LocationCheckpoint?,
    onProvider: (MapProvider) -> Unit,
    onFreeMapStyle: (FreeMapStyle) -> Unit,
    onAppearance: (MapAppearance) -> Unit,
    onDefaultSpeed: (WalkSpeedPreset) -> Unit,
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
    Column(
        Modifier.fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("‹ 返回地圖")
            }
            Text("設定", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = foreground)
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = palette.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("地圖與外觀", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapBasemapPolicy.availableProviders(true).forEach { option ->
                            FilterChip(
                                selected = provider == option,
                                onClick = { onProvider(option) },
                                label = { Text(if (option == MapProvider.OPEN_FREE_MAP)
                                    "OpenFreeMap" else "Google Maps") },
                            )
                        }
                    }
                }
                if (provider == MapProvider.OPEN_FREE_MAP) {
                    Text("地圖樣式", fontSize = 12.sp)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        FreeMapStyle.entries.forEach { option ->
                            FilterChip(
                                selected = freeMapStyle == option,
                                onClick = { onFreeMapStyle(option) },
                                label = { Text(option.label, fontSize = 12.sp) },
                            )
                        }
                    }
                }
                Text("介面外觀", color = foreground, fontSize = 12.sp)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(
                        MapAppearance.SYSTEM to "跟隨系統",
                        MapAppearance.LIGHT to "淺色",
                        MapAppearance.DARK to "深色",
                    ).forEach { (option, label) ->
                        FilterChip(
                            selected = appearance == option,
                            onClick = { onAppearance(option) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = palette.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("行走預設", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    WalkSpeedPreset.entries.forEach { option ->
                        FilterChip(
                            selected = defaultSpeed == option,
                            onClick = { onDefaultSpeed(option) },
                            label = { Text(option.display) },
                        )
                    }
                }
                Text("路線中可使用自訂速度或速度區間；首次開啟仍使用你儲存的預設速度。",
                    color = muted, fontSize = 12.sp)
                VirtualStrideEditor(
                    strideMetres = virtualStrideMetres,
                    onApply = onVirtualStrideMetres,
                    onMessage = onMessage,
                    muted = muted,
                )
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
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = palette.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("權限與控制", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                TextButton(onClick = onPermissions) { Text("檢查 Android 權限與模擬位置") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("懸浮控制", color = foreground, fontSize = 14.sp)
                    Switch(checked = overlayVisible, onCheckedChange = { onOverlay() })
                }
                if (statusMessage.contains("懸浮") || statusMessage.contains("授權")) {
                    Text(statusMessage, color = muted, fontSize = 12.sp)
                }
                Text("懸浮控制教學", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("首次開啟顯示教學", color = foreground, fontSize = 14.sp)
                    }
                    Switch(checked = overlayGuideEnabled, onCheckedChange = onOverlayGuideEnabled)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("長按圖示顯示名稱", color = foreground, fontSize = 14.sp)
                    }
                    Switch(checked = overlayHoldHints, onCheckedChange = onOverlayHoldHints)
                }
                TextButton(onClick = onReplayOverlayGuide) {
                    Text("查看懸浮控制操作說明")
                }
                BackgroundSessionPanel(
                    isMockActive = mockActive,
                    checkpoint = checkpoint,
                    foreground = foreground,
                    muted = muted,
                    onMessage = onMessage,
                    onAskLocationPermission = onAskLocation,
                )
            }
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = palette.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wi-Fi 遠端控制 · 選用", fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, color = foreground)
                Text("只在你主動開啟時於私人 Wi-Fi 使用，需配對碼及手機核准。" +
                    "關閉後立即撤銷連線；不影響路徑行走。",
                    fontSize = 12.sp, color = muted)
                TextButton(onClick = onToggleWifi) {
                    Text(if (showWifiPanel) "收起 Wi-Fi 控制設定" else "設定 Wi-Fi 控制")
                }
                if (showWifiPanel) WifiControlPanel(
                    isMockActive = mockActive,
                    foreground = foreground,
                    muted = muted,
                    onMessage = onMessage,
                )
            }
        }
        HealthConnectSettings(dark = dark, onMessage = onMessage)
    }
}
