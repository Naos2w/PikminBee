package com.naos.pikminbee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.MapAppearance
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.ui.LocationMap

private val navy = Color(0xFF18263A)
private val honey = Color(0xFF9F5E0C)
private val lightGlass = Color.White.copy(alpha = 0.91f)
private val darkGlass = Color(0xFF1E2B3C).copy(alpha = 0.91f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PikminBeeScreen() }
    }
}

private enum class ToolTab(val label: String) {
    TELEPORT("瞬移"), ROUTE("路徑"), FLOWERS("尋花"), SAVED("收藏")
}

@Composable
private fun FrostedCard(dark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) darkGlass else lightGlass),
        border = BorderStroke(1.dp, if (dark) Color.White.copy(alpha = .24f) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
    ) { content() }
}

@Composable
private fun PikminBeeScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var provider by remember { mutableStateOf(prefs.provider()) }
    var appearance by remember { mutableStateOf(prefs.appearance()) }
    val dark = when (appearance) {
        MapAppearance.SYSTEM -> systemDark
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
    val foreground = if (dark) Color(0xFFEFF5FF) else navy
    val muted = if (dark) Color(0xFFC1D0E3) else Color(0xFF6A778C)

    var selected by remember { mutableStateOf(Coordinate(25.0330, 121.5654)) }
    var latitudeText by remember { mutableStateOf(selected.latitude.toString()) }
    var longitudeText by remember { mutableStateOf(selected.longitude.toString()) }
    var tab by remember { mutableStateOf(ToolTab.TELEPORT) }
    var places by remember { mutableStateOf(prefs.places()) }
    var message by remember { mutableStateOf("地圖點一下就能選擇位置") }
    val mockStatus by LocationStatus.display.collectAsState()
    val mockPosition by LocationStatus.selected.collectAsState()

    val requestLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) "已取得定位權限，可以再次點選瞬移" else "請先授予精確定位權限"
    }

    LaunchedEffect(selected) {
        latitudeText = selected.latitude.toString()
        longitudeText = selected.longitude.toString()
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF091321) else Color(0xFFF4F7FC))) {
            LocationMap(
                provider = provider, dark = dark, selected = selected,
                onPick = {
                    selected = it
                    message = "已選位置：${it.label()}"
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                FrostedCard(dark, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("PikminBee", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = foreground)
                                Text("尋花採蜜 · $mockStatus", fontSize = 12.sp, color = muted)
                            }
                            Text("v0.1 DEV", fontSize = 11.sp, color = honey, fontWeight = FontWeight.SemiBold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(
                                MapProvider.OPEN_FREE_MAP to "OpenFreeMap",
                                MapProvider.GOOGLE_MAPS to "Google Maps"
                            ).forEach { (p, label) ->
                                FilterChip(
                                    selected = provider == p,
                                    onClick = {
                                        provider = p
                                        prefs.setProvider(p)
                                        if (p == MapProvider.GOOGLE_MAPS && !BuildConfig.HAS_GOOGLE_MAPS_KEY)
                                            message = "Google Maps 需要先設定 API Key"
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                MapAppearance.SYSTEM to "自動",
                                MapAppearance.LIGHT to "淺色",
                                MapAppearance.DARK to "深色",
                            ).forEach { (option, label) ->
                                FilterChip(
                                    selected = appearance == option,
                                    onClick = {
                                        appearance = option
                                        prefs.setAppearance(option)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                }

                FrostedCard(dark, Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ToolTab.entries.forEach { option ->
                                Box(
                                    modifier = Modifier.weight(1f).height(45.dp)
                                        .background(
                                            if (tab == option) honey else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { tab = option },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        option.label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (tab == option) Color.White else foreground
                                    )
                                }
                            }
                        }

                        when (tab) {
                            ToolTab.TELEPORT -> {
                                Text("瞬移到指定位置", fontWeight = FontWeight.Bold, color = foreground)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = latitudeText, onValueChange = { latitudeText = it.take(22) },
                                        label = { Text("緯度 Latitude") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = longitudeText, onValueChange = { longitudeText = it.take(22) },
                                        label = { Text("經度 Longitude") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val coordinate = Coordinate.parse(latitudeText, longitudeText)
                                            when {
                                                coordinate == null -> message = "經緯度格式不正確"
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                                    != PackageManager.PERMISSION_GRANTED -> requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                                else -> {
                                                    selected = coordinate
                                                    runCatching {
                                                        ContextCompat.startForegroundService(
                                                            context, MockLocationService.teleportIntent(context, coordinate))
                                                    }.onFailure { message = "啟動失敗：${it.message}" }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = honey)
                                    ) { Text("瞬移並停留") }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { context.startService(MockLocationService.stopIntent(context)) }
                                            message = "正在恢復真實定位"
                                        },
                                        modifier = Modifier.weight(.9f).height(52.dp),
                                    ) { Text("恢復真實 GPS") }
                                }
                                if (mockPosition != null) {
                                    Text("模擬座標：${mockPosition!!.label()}", fontSize = 12.sp, color = muted)
                                }
                                TextButton(
                                    onClick = {
                                        prefs.addPlace("地點 ${places.size + 1}", selected)
                                        places = prefs.places()
                                        message = "已收藏目前選取位置"
                                    }
                                ) { Text("＋ 收藏這個位置") }
                            }
                            ToolTab.SAVED -> {
                                Text("收藏地點", fontWeight = FontWeight.Bold, color = foreground)
                                if (places.isEmpty()) {
                                    Text("從地圖選擇位置後，到「瞬移」儲存收藏。", color = muted)
                                }
                                Column(
                                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    places.forEach { place ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = {
                                                selected = place.coordinate
                                                tab = ToolTab.TELEPORT
                                            }) {
                                                Text("${place.name} · ${place.coordinate.label()}", fontSize = 12.sp)
                                            }
                                            TextButton(onClick = {
                                                prefs.removePlace(place.id)
                                                places = prefs.places()
                                            }) { Text("刪除", fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }
                            ToolTab.ROUTE -> {
                                Text("多點道路路線", fontWeight = FontWeight.Bold, color = foreground)
                                Text("下一階段：地圖依序選點、道路規劃、開始／暫停／停止／清除。",
                                    color = muted, fontSize = 13.sp)
                            }
                            ToolTab.FLOWERS -> {
                                Text("尋花模式", fontWeight = FontWeight.Bold, color = foreground)
                                Text("下一階段：大花清單、路線巡迴與自動尋花。",
                                    color = muted, fontSize = 13.sp)
                            }
                        }
                        Text(message, fontSize = 12.sp, color = muted, maxLines = 2)
                    }
                }
            }
        }
    }
}
