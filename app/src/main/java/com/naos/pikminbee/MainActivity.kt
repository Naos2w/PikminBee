package com.naos.pikminbee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.naos.pikminbee.flower.FlowerCatalogStore
import com.naos.pikminbee.flower.FlowerCatalogState
import com.naos.pikminbee.flower.FlowerFinder
import com.naos.pikminbee.flower.FlowerJsonCodec
import com.naos.pikminbee.flower.FlowerImport
import com.naos.pikminbee.data.MapAppearance
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.SavedRouteState
import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.gpx.GpxCodec
import com.naos.pikminbee.gpx.GpxImport
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.route.ValhallaPlanner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.location.CheckpointStatus
import com.naos.pikminbee.location.SessionJournal
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.ui.LocationMap
import com.naos.pikminbee.ui.JourneyControls
import com.naos.pikminbee.ui.RouteLibraryPanel
import com.naos.pikminbee.ui.FlowerCatalogPanel
import com.naos.pikminbee.ui.WifiControlPanel
import com.naos.pikminbee.ui.BackgroundSessionPanel

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
    val flowerCatalog = remember(context) { FlowerCatalogStore(context) }
    val allFlowers by FlowerCatalogState.spots.collectAsState()
    remember(context) { SessionJournal(context).also { it.read() } }
    val checkpoint by CheckpointStatus.last.collectAsState()
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
    var showWifiPanel by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(prefs.places()) }
    var message by remember { mutableStateOf("地圖點一下就能選擇位置") }
    val roadStops by RouteDraftState.routeStops.collectAsState()
    val flowerStops by RouteDraftState.flowerStops.collectAsState()
    val savedRoutes by SavedRouteState.routes.collectAsState()
    var planning by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.4) }
    var flowerLoop by remember { mutableStateOf(true) }
    val work = rememberCoroutineScope()
    val mockStatus by LocationStatus.display.collectAsState()
    val mockPosition by LocationStatus.selected.collectAsState()
    val walkPhase by LocationStatus.phase.collectAsState()
    val walkMode by LocationStatus.mode.collectAsState()
    val roadLine by LocationStatus.geometry.collectAsState()
    val overlayVisible by LocationStatus.overlayVisible.collectAsState()

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) "已取得定位權限，可以再次點選瞬移" else "請先授予精確定位權限"
    }

    var importMode by remember { mutableStateOf(WalkMode.ROUTE) }
    var exportRouteId by remember { mutableStateOf<String?>(null) }
    var pendingSimplifiedGpx by remember { mutableStateOf<GpxImport?>(null) }
    var pendingFlowerImport by remember { mutableStateOf<FlowerImport?>(null) }

    fun acceptImportedGpx(imported: GpxImport) {
        runCatching {
            val route = prefs.addSavedRoute(imported.name, imported.mode, imported.stops)
            prefs.loadSavedRouteIntoDraft(route)
            tab = if (route.mode == WalkMode.FLOWERS) ToolTab.FLOWERS else ToolTab.ROUTE
            message = "已匯入並收藏「" + route.name + "」，共 " + route.stops.size + " 個路點"
        }.onFailure {
            message = "GPX 匯入失敗：" + (it.message ?: "無法儲存收藏")
        }
    }

    // Both pickers use Android's Storage Access Framework. No broad storage permission.
    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("無法讀取選擇的 GPX 檔案")
                    stream.use { GpxCodec.decode(it, importMode) }
                }
                if (imported.simplified) {
                    pendingSimplifiedGpx = imported
                    message = "此 GPX 軌跡需要簡化路點，請確認是否匯入"
                } else acceptImportedGpx(imported)
            } catch (e: Exception) {
                message = "GPX 匯入失敗：" + (e.message ?: "檔案格式無效")
            }
        }
    }

    val exportGpx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null) {
            val route = savedRoutes.firstOrNull { it.id == exportRouteId }
            if (route == null) message = "找不到要匯出的收藏路線"
            else work.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalArgumentException("無法寫入選取的位置")
                        stream.use { it.write(GpxCodec.encode(route)) }
                    }
                    message = "已匯出「" + route.name + "」為 GPX"
                } catch (e: Exception) {
                    message = "GPX 匯出失敗：" + (e.message ?: "儲存失敗")
                }
            }
            exportRouteId = null
        }
    }

    val importFlowers = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val source = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("無法讀取選擇的花點檔案")
                    source.use { FlowerJsonCodec.decode(it) }
                }
                pendingFlowerImport = result
                message = "檢查匯入來源與花點數量，再選擇是否加入"
            } catch (e: Exception) {
                message = "花點匯入失敗：" + (e.message ?: "檔案無效")
            }
        }
    }

    val exportFlowers = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) work.launch {
            try {
                val snapshot = flowerCatalog.all()
                val contents = withContext(Dispatchers.Default) {
                    FlowerJsonCodec.encodeGeoJson(snapshot)
                }
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalArgumentException("無法寫入選取的位置")
                    stream.use { it.write(contents) }
                }
                message = "已匯出 " + snapshot.size + " 個使用者記錄的候選花點"
            } catch (e: Exception) {
                message = "花點匯出失敗：" + (e.message ?: "檔案無法寫入")
            }
        }
    }

    fun saveRoute(name: String, mode: WalkMode, stops: List<Coordinate>): Boolean =
        runCatching {
            val item = prefs.addSavedRoute(name, mode, stops)
            message = "已收藏「" + item.name + "」，共 " + item.stops.size + " 個路點"
        }.onFailure { message = "無法收藏路線：" + (it.message ?: "儲存錯誤") }.isSuccess

    fun renameRoute(route: SavedRoute, name: String): Boolean =
        runCatching {
            check(prefs.renameSavedRoute(route.id, name)) { "路線已不存在" }
            message = "已修改路線名稱"
        }.onFailure { message = "無法改名：" + (it.message ?: "儲存錯誤") }.isSuccess

    fun loadRoute(route: SavedRoute) {
        prefs.loadSavedRouteIntoDraft(route)
        tab = if (route.mode == WalkMode.FLOWERS) ToolTab.FLOWERS else ToolTab.ROUTE
        message = "已載入「" + route.name + "」的路點，沒有啟動行走"
    }

    fun deleteRoute(route: SavedRoute) {
        message = if (prefs.deleteSavedRoute(route.id)) "已刪除「" + route.name + "」"
            else "找不到要刪除的路線"
    }

    LaunchedEffect(selected) {
        latitudeText = selected.latitude.toString()
        longitudeText = selected.longitude.toString()
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF091321) else Color(0xFFF4F7FC))) {
            LocationMap(
                provider = provider, dark = dark,
                selected = mockPosition ?: when (tab) {
                    ToolTab.ROUTE -> roadStops.firstOrNull() ?: selected
                    ToolTab.FLOWERS -> selected
                    else -> selected
                },
                stops = when (tab) {
                    ToolTab.ROUTE -> roadStops
                    ToolTab.FLOWERS -> flowerStops
                    else -> emptyList()
                },
                road = roadLine,
                flowerSpots = if (tab == ToolTab.FLOWERS) FlowerFinder.nearby(
                    selected, allFlowers, 5000.0, maxCount = 100
                ) else emptyList(),
                onPick = { point ->
                    when (tab) {
                        ToolTab.ROUTE -> {
                            if (roadStops.size < 15) {
                                prefs.saveRouteDraft(roadStops + point)
                                message = "已新增路點 ${roadStops.size + 1} · 請繼續點選地圖"
                            } else message = "最多只能加入 15 個路點"
                        }
                        ToolTab.FLOWERS -> {
                            selected = point
                            if (flowerStops.size < 15) {
                                prefs.saveFlowerDraft(flowerStops + point)
                                message = "已標記第 ${flowerStops.size + 1} 個花點"
                            } else message = "最多只能加入 15 個花點"
                        }
                        else -> {
                            selected = point
                            message = "已選位置：${point.label()}"
                        }
                    }
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
                            Text("v0.2 DEV", fontSize = 11.sp, color = honey, fontWeight = FontWeight.SemiBold)
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
                        Modifier.fillMaxWidth().heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()).padding(14.dp),
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
                                JourneyControls(
                                    mode = WalkMode.ROUTE,
                                    stops = roadStops,
                                    origin = mockPosition ?: roadStops.firstOrNull() ?: selected,
                                    activeMode = walkMode,
                                    phase = walkPhase,
                                    foreground = foreground, muted = muted,
                                    onUndo = { prefs.saveRouteDraft(roadStops.dropLast(1)) },
                                    onClear = { prefs.saveRouteDraft(emptyList()) },
                                    onMessage = { message = it },
                                    onAskLocation = { requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                )
                                RouteLibraryPanel(
                                    mode = WalkMode.ROUTE, draft = roadStops,
                                    routes = savedRoutes.filter { it.mode == WalkMode.ROUTE },
                                    foreground = foreground, muted = muted,
                                    onSave = { name -> saveRoute(name, WalkMode.ROUTE, roadStops) },
                                    onRename = ::renameRoute,
                                    onLoad = ::loadRoute,
                                    onExport = { route ->
                                        exportRouteId = route.id
                                        exportGpx.launch(route.fileName())
                                    },
                                    onDelete = ::deleteRoute,
                                    onImport = {
                                        importMode = WalkMode.ROUTE
                                        importGpx.launch(arrayOf("application/gpx+xml", "application/xml",
                                            "text/xml", "*/*"))
                                    },
                                )
                            }
                            ToolTab.FLOWERS -> {
                                JourneyControls(
                                    mode = WalkMode.FLOWERS,
                                    stops = flowerStops,
                                    origin = mockPosition ?: flowerStops.firstOrNull() ?: selected,
                                    activeMode = walkMode,
                                    phase = walkPhase,
                                    foreground = foreground, muted = muted,
                                    onUndo = { prefs.saveFlowerDraft(flowerStops.dropLast(1)) },
                                    onClear = { prefs.saveFlowerDraft(emptyList()) },
                                    onMessage = { message = it },
                                    onAskLocation = { requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                )
                                FlowerCatalogPanel(
                                    spots = allFlowers,
                                    origin = mockPosition ?: selected,
                                    selected = selected,
                                    draft = flowerStops,
                                    foreground = foreground, muted = muted,
                                    onRecord = { name, point ->
                                        runCatching { flowerCatalog.addManual(name, point) }
                                            .onSuccess { message = "已記錄／本人確認「" + it.name + "」" }
                                            .onFailure { message = "無法記錄花點：" + it.message }
                                    },
                                    onMarkChecked = { flower ->
                                        if (flowerCatalog.markChecked(flower.id)) {
                                            message = "已將「" + flower.name + "」標為本人確認"
                                        }
                                    },
                                    onRemove = { flower ->
                                        if (flowerCatalog.remove(flower.id)) {
                                            message = "已移除花點「" + flower.name + "」"
                                        }
                                    },
                                    onAddStop = { flower ->
                                        val candidate = FlowerFinder.appendToDraft(
                                            mockPosition ?: selected, flowerStops, listOf(flower), 14
                                        )
                                        if (candidate.size == flowerStops.size) {
                                            message = "此花點已在路線內，或已達花點數量上限"
                                        } else {
                                            prefs.saveFlowerDraft(candidate)
                                            message = "已將「" + flower.name + "」加入尋花路線"
                                        }
                                    },
                                    onAppendNearby = { candidates ->
                                        val added = FlowerFinder.appendToDraft(
                                            mockPosition ?: selected, flowerStops, candidates, 14
                                        )
                                        if (added.size == flowerStops.size) {
                                            message = "附近沒有可加入的新花點"
                                        } else {
                                            prefs.saveFlowerDraft(added)
                                            message = "已新增 " + (added.size - flowerStops.size) +
                                                " 個候選花點；開始行走前會重新規劃道路"
                                        }
                                    },
                                    onImport = {
                                        importFlowers.launch(arrayOf("application/json", "application/geo+json", "*/*"))
                                    },
                                    onExport = {
                                        exportFlowers.launch("PikminBee-flowers.geojson")
                                    },
                                )
                                RouteLibraryPanel(
                                    mode = WalkMode.FLOWERS, draft = flowerStops,
                                    routes = savedRoutes.filter { it.mode == WalkMode.FLOWERS },
                                    foreground = foreground, muted = muted,
                                    onSave = { name -> saveRoute(name, WalkMode.FLOWERS, flowerStops) },
                                    onRename = ::renameRoute,
                                    onLoad = ::loadRoute,
                                    onExport = { route ->
                                        exportRouteId = route.id
                                        exportGpx.launch(route.fileName())
                                    },
                                    onDelete = ::deleteRoute,
                                    onImport = {
                                        importMode = WalkMode.FLOWERS
                                        importGpx.launch(arrayOf("application/gpx+xml", "application/xml",
                                            "text/xml", "*/*"))
                                    },
                                )
                            }
                        }
                        TextButton(onClick = {
                            when {
                                mockPosition == null -> message = "請先啟動模擬定位，再開啟懸浮控制"
                                !Settings.canDrawOverlays(context) -> {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)))
                                    message = "請授予懸浮顯示權限，返回後再點一次"
                                }
                                else -> {
                                    val action = if (overlayVisible) MockLocationService.ACTION_HIDE_OVERLAY
                                    else MockLocationService.ACTION_SHOW_OVERLAY
                                    context.startService(MockLocationService.command(context, action))
                                }
                            }
                        }) {
                            Text(if (overlayVisible) "🐝 隱藏懸浮控制" else "🐝 開啟懸浮控制",
                                fontSize = 13.sp)
                        }
                        TextButton(onClick = { showWifiPanel = !showWifiPanel }) {
                            Text(if (showWifiPanel) "收起 Wi-Fi 遠端控制" else "Wi-Fi 遠端控制",
                                fontSize = 13.sp)
                        }
                        if (showWifiPanel) WifiControlPanel(
                            isMockActive = mockPosition != null,
                            foreground = foreground,
                            muted = muted,
                            onMessage = { message = it },
                        )
                        BackgroundSessionPanel(
                            isMockActive = mockPosition != null,
                            checkpoint = checkpoint,
                            foreground = foreground,
                            muted = muted,
                            onMessage = { message = it },
                            onAskLocationPermission = {
                                requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                        )
                        Text(message, fontSize = 12.sp, color = muted, maxLines = 2)
                    }
                }
            }
        }
        pendingSimplifiedGpx?.let { candidate ->
            AlertDialog(
                onDismissRequest = { pendingSimplifiedGpx = null },
                title = { Text("簡化 GPX 軌跡再匯入？") },
                text = {
                    Text(
                        "原始軌跡有 " + candidate.originalPoints + " 個點，將保留 " +
                            candidate.stops.size + " 個主要路點。這不會原樣重播 GPX 軌跡；" +
                            "之後開始行走時仍需重新規劃行人道路。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        acceptImportedGpx(candidate)
                        pendingSimplifiedGpx = null
                    }) { Text("確認匯入") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingSimplifiedGpx = null
                        message = "已取消匯入，原有收藏與路點不變"
                    }) { Text("取消") }
                },
            )
        }
        pendingFlowerImport?.let { candidate ->
            AlertDialog(
                onDismissRequest = { pendingFlowerImport = null },
                title = { Text("匯入使用者提供的花點？") },
                text = {
                    Text(
                        "檔案格式：" + candidate.source +
                            "。標記花點 " + candidate.spots.size + " 筆，略過非花點 " +
                            candidate.ignored + " 筆。匯入資料全部標示「待確認」，" +
                            "不會當成 Pikmin Bloom 官方即時大花，也不會自動開始行走。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { flowerCatalog.importCandidates(candidate) }
                            .onSuccess { result ->
                                message = "花點匯入完成：新增 " + result.added +
                                    " 筆，略過近距離重複 " + result.duplicates + " 筆"
                            }.onFailure {
                                message = "花點匯入失敗：" + (it.message ?: "無法保存")
                            }
                        pendingFlowerImport = null
                    }) { Text("確認匯入") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingFlowerImport = null
                        message = "已取消花點匯入，原有資料不變"
                    }) { Text("取消") }
                },
            )
        }
    }
}
