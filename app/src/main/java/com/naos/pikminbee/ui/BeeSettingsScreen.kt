package com.naos.pikminbee.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.data.FreeMapStyle
import com.naos.pikminbee.data.MapAppearance
import com.naos.pikminbee.data.MapBasemapPolicy
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.location.LocationCheckpoint

/** Common preferences live here, never repeated on each mode's map sheet. */
@Composable
fun BeeSettingsScreen(
    dark: Boolean,
    provider: MapProvider,
    freeMapStyle: FreeMapStyle,
    appearance: MapAppearance,
    defaultSpeed: WalkSpeedPreset,
    overlayVisible: Boolean,
    mockActive: Boolean,
    showWifiPanel: Boolean,
    checkpoint: LocationCheckpoint?,
    onProvider: (MapProvider) -> Unit,
    onFreeMapStyle: (FreeMapStyle) -> Unit,
    onAppearance: (MapAppearance) -> Unit,
    onDefaultSpeed: (WalkSpeedPreset) -> Unit,
    onPermissions: () -> Unit,
    onOverlay: () -> Unit,
    onToggleWifi: () -> Unit,
    onAskLocation: () -> Unit,
    onMessage: (String) -> Unit,
    statusMessage: String,
    onBack: () -> Unit,
) {
    val foreground = if (dark) Color(0xFFF3F7FF) else Color(0xFF18263A)
    val muted = if (dark) Color(0xFFC6D3E6) else Color(0xFF52627A)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(if (dark)
                listOf(Color(0xFF142541), Color(0xFF0C152A), Color(0xFF1C3150))
                else listOf(Color(0xFFDFEBFF), Color(0xFFF8FBFF), Color(0xFFE8EFFF))))
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回地圖") }
            Text("共用設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = foreground)
            Text("UAT", color = muted, fontSize = 11.sp)
        }
        MapGlass(dark, Modifier.fillMaxWidth()) {
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
                } else {
                    Text("OpenFreeMap · 免 Google API Key", color = muted, fontSize = 12.sp)
                }
                if (provider == MapProvider.OPEN_FREE_MAP) {
                    Text("地圖樣式（圖磚需網路）", fontSize = 12.sp)
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
                Text("介面主題", fontSize = 12.sp)
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
        MapGlass(dark, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("行走預設", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("個人預設：" + defaultSpeed.display, color = muted, fontSize = 12.sp)
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
                Text("行走時仍可在路徑／尋花面板臨時調速，個人預設不會被覆蓋。",
                    color = muted, fontSize = 12.sp)
            }
        }
        MapGlass(dark, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("權限與控制", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                TextButton(onClick = onPermissions) { Text("檢查 Android 權限與模擬位置") }
                TextButton(onClick = onOverlay) {
                    Text(if (overlayVisible) "🐝 隱藏懸浮控制" else "🐝 開啟懸浮控制")
                }
                Text(
                    if (overlayVisible) "懸浮控制已顯示，點蜜蜂圖示可展開功能。"
                    else "開啟時會檢查「顯示在其他應用程式上層」權限，無需先啟動模擬定位。",
                    color = muted, fontSize = 12.sp,
                )
                Text(statusMessage, color = foreground, fontSize = 12.sp)
                TextButton(onClick = onToggleWifi) {
                    Text(if (showWifiPanel) "收起 Wi-Fi 遠端控制" else "Wi-Fi 遠端控制")
                }
                if (showWifiPanel) WifiControlPanel(
                    isMockActive = mockActive,
                    foreground = foreground,
                    muted = muted,
                    onMessage = onMessage,
                )
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
        Text("PikminBee UAT · 僅使用者明確操作才會啟動模擬定位",
            fontSize = 12.sp, color = muted)
    }
}
