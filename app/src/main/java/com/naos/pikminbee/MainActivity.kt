package com.naos.pikminbee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
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
import com.naos.pikminbee.data.FreeMapStyle
import com.naos.pikminbee.data.MapBasemapPolicy
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.data.SavedRouteState
import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.gpx.GpxCodec
import com.naos.pikminbee.gpx.GpxImport
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.MapCameraGeometry
import com.naos.pikminbee.domain.FlowerMapDraft
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
import com.naos.pikminbee.ui.MapCameraRequest
import com.naos.pikminbee.ui.JourneyControls
import com.naos.pikminbee.ui.RouteLibraryPanel
import com.naos.pikminbee.ui.FlowerCatalogPanel
import com.naos.pikminbee.ui.FlowerMapControls
import com.naos.pikminbee.ui.FlowerMapPick
import com.naos.pikminbee.ui.FlowerVisionPanel
import com.naos.pikminbee.ui.WifiControlPanel
import com.naos.pikminbee.ui.BackgroundSessionPanel
import com.naos.pikminbee.ui.MapFirstShell
import com.naos.pikminbee.ui.BeeSettingsScreen
import com.naos.pikminbee.ui.BeePermissionWizard
import com.naos.pikminbee.ui.PikminBeeThemeColors

private val navy = Color(0xFF18263A)
private val honey = Color(0xFF9F5E0C)
private val lightGlass = Color.White.copy(alpha = 0.975f)
private val darkGlass = Color(0xFF1E2B3C).copy(alpha = 0.985f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PikminBeeScreen() }
    }
}

private enum class BeePage { MAP, SETTINGS, PERMISSIONS }

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
    var freeMapStyle by remember { mutableStateOf(prefs.freeMapStyle()) }
    var appearance by remember { mutableStateOf(prefs.appearance()) }
    val dark = when (appearance) {
        MapAppearance.SYSTEM -> systemDark
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
    val foreground = if (dark) Color(0xFFEFF5FF) else navy
    val muted = if (dark) Color(0xFFC1D0E3) else Color(0xFF6A778C)

    var selected by remember { mutableStateOf(Coordinate(25.0330, 121.5654)) }
    var flowerPick by remember { mutableStateOf<FlowerMapPick?>(null) }
    var editingFlowerStopIndex by remember { mutableStateOf<Int?>(null) }
    // A recorded flower is never silently treated as the route's starting point.
    var flowerStart by remember { mutableStateOf<Coordinate?>(null) }
    var flowerRoadPreview by remember { mutableStateOf(emptyList<Coordinate>()) }
    var mapExplore by remember { mutableStateOf(false) }
    var cameraSerial by remember { mutableIntStateOf(0) }
    var cameraRequest by remember { mutableStateOf<MapCameraRequest?>(null) }
    val controlsScroll = rememberScrollState()
    var latitudeText by remember { mutableStateOf(selected.latitude.toString()) }
    var longitudeText by remember { mutableStateOf(selected.longitude.toString()) }
    var tab by remember { mutableStateOf(ToolTab.TELEPORT) }
    LaunchedEffect(tab) { controlsScroll.scrollTo(0) }
    var showWifiPanel by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(prefs.places()) }
    var message by remember { mutableStateOf("地圖點一下就能選擇位置") }
    val roadStops by RouteDraftState.routeStops.collectAsState()
    val flowerStops by RouteDraftState.flowerStops.collectAsState()
    val savedRoutes by SavedRouteState.routes.collectAsState()
    var planning by remember { mutableStateOf(false) }
    val speedPreset by WalkSpeedState.selected.collectAsState()
    val defaultSpeedPreset by WalkSpeedState.preferred.collectAsState()
    var flowerLoop by remember { mutableStateOf(true) }
    val work = rememberCoroutineScope()
    val mockStatus by LocationStatus.display.collectAsState()
    val mockPosition by LocationStatus.selected.collectAsState()
    val walkPhase by LocationStatus.phase.collectAsState()
    val walkMode by LocationStatus.mode.collectAsState()
    val roadLine by LocationStatus.geometry.collectAsState()
    val overlayVisible by LocationStatus.overlayVisible.collectAsState()
    LaunchedEffect(flowerStops, flowerStart, mockPosition) {
        flowerRoadPreview = emptyList()
    }
    LaunchedEffect(flowerStops) {
        // Any draft mutation invalidates the previous marker index.
        editingFlowerStopIndex = null
    }
    LaunchedEffect(flowerPick, editingFlowerStopIndex, tab) {
        if (tab == ToolTab.FLOWERS &&
            (flowerPick != null || editingFlowerStopIndex != null)) controlsScroll.animateScrollTo(0)
    }

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

    val activeFlowerWalk = walkMode == WalkMode.FLOWERS &&
        walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
    val activeRouteWalk = walkMode == WalkMode.ROUTE &&
        walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
    val displayedRoad = when (tab) {
        ToolTab.FLOWERS -> if (activeFlowerWalk) roadLine else flowerRoadPreview
        ToolTab.ROUTE -> if (activeRouteWalk) roadLine else emptyList()
        else -> emptyList()
    }
    fun chooseWalkSpeed(next: WalkSpeedPreset) {
        prefs.setWalkSpeedPreset(next)
        val moving = mockPosition != null &&
            walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
        if (moving) {
            runCatching {
                context.startService(MockLocationService.speedIntent(context, next))
            }.onFailure {
                message = "速度已儲存，但無法即時套用：${it.message}"
                return
            }
        }
        message = "速度已選擇：${next.display}" +
            if (moving) " · 行走中已要求更新" else " · 下次行走時使用"
    }
    fun setDefaultWalkSpeed(next: WalkSpeedPreset) {
        prefs.setDefaultWalkSpeedPreset(next)
        message = "已將 ${next.display} 設為下次開啟 App 的預設速度"
    }
    fun moveMapCamera(target: MapCameraGeometry.ViewTarget) {
        if (!target.hasAny) return
        cameraSerial++
        cameraRequest = MapCameraRequest(cameraSerial, target)
        if (target.crossesDateLine) {
            message = "路線跨越國際換日線，暫先聚焦起點；可手動縮放地圖"
        }
    }
    fun fitCurrentRoute() {
        val target = when (tab) {
            ToolTab.FLOWERS -> MapCameraGeometry.forRoute(
                mockPosition ?: flowerStart, flowerStops, displayedRoad,
            )
            ToolTab.ROUTE -> MapCameraGeometry.forRoute(
                mockPosition ?: roadStops.firstOrNull() ?: selected, roadStops, displayedRoad,
            )
            else -> MapCameraGeometry.fitPoints(listOf(selected))
        }
        mapExplore = true
        moveMapCamera(if (target.hasAny) target
            else MapCameraGeometry.fitPoints(listOf(selected)))
    }
    fun focusMapPoint(point: Coordinate) {
        mapExplore = true
        moveMapCamera(MapCameraGeometry.fitPoints(listOf(point)))
    }

    var activePage by remember {
        mutableStateOf(if (prefs.setupCompleted()) BeePage.MAP else BeePage.PERMISSIONS)
    }
    var coordinateInput by remember { mutableStateOf(selected.label()) }
    LaunchedEffect(selected) { coordinateInput = selected.label() }
    fun applyCoordinateInput() {
        val parsed = Coordinate.parseCombined(coordinateInput)
        if (parsed == null) {
            message = "請輸入經緯度，例如 25.0330, 121.5654"
            return
        }
        selected = parsed
        if (tab == ToolTab.FLOWERS) flowerPick = FlowerMapPick(parsed)
        message = "已選取 " + parsed.label() + " · 不會自動瞬移"
        focusMapPoint(parsed)
    }
    fun goToCurrentMapPosition() {
        val virtual = mockPosition
        if (virtual != null) {
            focusMapPoint(virtual)
            message = "已移至目前模擬位置，沒有瞬移"
            return
        }
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activePage = BeePage.PERMISSIONS
            message = "需要精確定位權限，請在權限檢查頁設定"
            return
        }
        try {
            val lm = context.getSystemService(android.location.LocationManager::class.java)
            val gps = android.location.LocationManager.GPS_PROVIDER
            val network = android.location.LocationManager.NETWORK_PROVIDER
            val providerName = if (lm.isProviderEnabled(gps)) gps else network
            val cached = lm.getLastKnownLocation(providerName)
            if (cached != null && cached.time > System.currentTimeMillis() - 300_000L) {
                focusMapPoint(Coordinate(cached.latitude, cached.longitude))
                message = "已移至最近的手機位置，沒有瞬移"
            } else if (android.os.Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(providerName, null, context.mainExecutor) { location ->
                    if (location == null) {
                        message = "暫時無法取得手機位置，請檢查定位服務"
                    } else {
                        runCatching { Coordinate(location.latitude, location.longitude) }
                            .onSuccess {
                                focusMapPoint(it)
                                message = "已移至手機目前位置，沒有瞬移"
                            }.onFailure { message = "手機位置座標無效" }
                    }
                }
                message = "正在取得手機目前位置…"
            } else {
                message = "尚無可用的手機位置，請先在地圖選取座標"
            }
        } catch (error: Exception) {
            message = "取得位置失敗：" + (error.message ?: "請檢查系統定位")
        }
    }
    BackHandler(enabled = activePage != BeePage.MAP || mapExplore) {
        if (activePage != BeePage.MAP) activePage = BeePage.MAP
        else mapExplore = false
    }

    MaterialTheme(colorScheme = PikminBeeThemeColors.forDarkMode(dark)) {
        when (activePage) {
            BeePage.PERMISSIONS -> BeePermissionWizard(
                dark = dark,
                initial = !prefs.setupCompleted(),
                onFinish = {
                    prefs.setSetupCompleted(true)
                    activePage = BeePage.MAP
                },
                onExit = { activePage = BeePage.MAP },
            )
            BeePage.SETTINGS -> BeeSettingsScreen(
                dark = dark,
                provider = provider,
                freeMapStyle = freeMapStyle,
                appearance = appearance,
                defaultSpeed = defaultSpeedPreset,
                overlayVisible = overlayVisible,
                mockActive = mockPosition != null,
                showWifiPanel = showWifiPanel,
                checkpoint = checkpoint,
                onProvider = {
                    provider = it
                    prefs.setProvider(it)
                },
                onFreeMapStyle = {
                    freeMapStyle = it
                    prefs.setFreeMapStyle(it)
                },
                onAppearance = {
                    appearance = it
                    prefs.setAppearance(it)
                },
                onDefaultSpeed = ::setDefaultWalkSpeed,
                onPermissions = { activePage = BeePage.PERMISSIONS },
                onOverlay = {
                    when {
                        mockPosition == null ->
                            message = "請先啟動模擬定位，才能開啟懸浮控制"
                        !Settings.canDrawOverlays(context) -> {
                            context.startActivity(Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName),
                            ))
                            message = "授予懸浮窗權限後，回 App 再點一次"
                        }
                        else -> context.startService(MockLocationService.command(
                            context,
                            if (overlayVisible) MockLocationService.ACTION_HIDE_OVERLAY
                            else MockLocationService.ACTION_SHOW_OVERLAY,
                        ))
                    }
                },
                onToggleWifi = { showWifiPanel = !showWifiPanel },
                onAskLocation = {
                    requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onMessage = { message = it },
                onBack = { activePage = BeePage.MAP },
            )
            BeePage.MAP -> MapFirstShell(
                dark = dark,
                modeIndex = tab.ordinal,
                modeTitle = tab.label,
                message = message,
                coordinateInput = coordinateInput,
                onCoordinateChange = { coordinateInput = it },
                onCoordinateApply = ::applyCoordinateInput,
                onCoordinateCopy = {
                    val clipboard = context.getSystemService(
                        android.content.ClipboardManager::class.java,
                    )
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                        "PikminBee coordinate", selected.label(),
                    ))
                    message = "已複製目前選取經緯度"
                },
                onCurrentLocation = ::goToCurrentMapPosition,
                onMode = { index ->
                    tab = ToolTab.entries[index]
                    mapExplore = false
                },
                onSettings = { activePage = BeePage.SETTINGS },
                onPermissions = { activePage = BeePage.PERMISSIONS },
                mapExplore = mapExplore,
                onCloseExplore = { mapExplore = false },
                mapContent = {
            LocationMap(
                provider = provider, dark = dark,
                selected = selected,
                start = if (tab == ToolTab.FLOWERS) mockPosition ?: flowerStart else null,
                stops = when (tab) {
                    ToolTab.ROUTE -> roadStops
                    ToolTab.FLOWERS -> flowerStops
                    else -> emptyList()
                },
                road = displayedRoad,
                previewRoad = tab == ToolTab.FLOWERS && !activeFlowerWalk &&
                    flowerRoadPreview.isNotEmpty(),
                freeMapStyle = freeMapStyle,
                cameraRequest = cameraRequest,
                mapExplore = mapExplore,
                onCameraFailure = { message = it },
                flowerSpots = if (tab == ToolTab.FLOWERS) FlowerFinder.nearby(
                    selected, allFlowers, 5000.0, maxCount = 100
                ) else emptyList(),
                onPick = { point ->
                    if (mapExplore) mapExplore = false
                    when (tab) {
                        ToolTab.ROUTE -> {
                            selected = point
                            message = "已選取經緯度，按「加入路點」才會新增至路線"
                        }
                        ToolTab.FLOWERS -> {
                            selected = point
                            flowerPick = FlowerMapPick(point)
                            message = editingFlowerStopIndex?.let {
                                "已選新位置，請確認是否替換第 ${it + 1} 個路點"
                            } ?: "已選擇地圖位置，請確認加入路線或設為起點"
                        }
                        else -> {
                            selected = point
                            message = "已選位置：${point.label()}"
                        }
                    }
                },
                onFlowerPick = { flower ->
                    if (mapExplore) mapExplore = false
                    selected = flower.coordinate
                    flowerPick = FlowerMapPick(flower.coordinate, flower)
                    message = editingFlowerStopIndex?.let {
                        "已選擇「${flower.name}」，可確認是否替換第 ${it + 1} 個路點"
                    } ?: "已選擇「${flower.name}」，可確認是否加入尋花路線"
                },
                onStopPick = if (tab == ToolTab.FLOWERS) { index: Int ->
                    flowerStops.getOrNull(index)?.let { stop ->
                        if (mapExplore) mapExplore = false
                        selected = stop
                        flowerPick = null
                        editingFlowerStopIndex = index
                        message = "已選擇第 ${index + 1} 個路點；點地圖挑選新位置或直接移除"
                    }
                } else null,
                modifier = Modifier.fillMaxSize()
            )
                },
                modeContent = {
                    if (tab == ToolTab.ROUTE) {
                        OutlinedButton(
                            onClick = {
                                if (roadStops.size >= 15) {
                                    message = "最多 15 個道路路點"
                                } else {
                                    prefs.saveRouteDraft(roadStops + selected)
                                    message = "已新增第 " + (roadStops.size + 1) + " 個路點"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("＋ 加入地圖所選路點") }
                    }
                        when (tab) {
                            ToolTab.TELEPORT -> {
                                Text("瞬移到指定位置", fontWeight = FontWeight.Bold, color = foreground)
                                Text("選取：" + selected.label(), color = muted, fontSize = 13.sp)
                                Text("上方座標列可貼上經緯度；點地圖只會選取，不會自動瞬移。",
                                    color = muted, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val coordinate = selected
                                            when {
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
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = honey, contentColor = Color.White,
                                        )
                                    ) { Text("瞬移並停留") }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { context.startService(MockLocationService.stopIntent(context)) }
                                            message = "正在恢復真實定位"
                                        },
                                        enabled = mockPosition != null,
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
                                Text("收藏的路線", fontWeight = FontWeight.SemiBold, color = foreground)
                                listOf(WalkMode.ROUTE, WalkMode.FLOWERS).forEach { kind ->
                                    RouteLibraryPanel(
                                        mode = kind,
                                        draft = if (kind == WalkMode.FLOWERS) flowerStops else roadStops,
                                        routes = savedRoutes.filter { it.mode == kind },
                                        foreground = foreground, muted = muted,
                                        onSave = { name ->
                                            saveRoute(name, kind,
                                                if (kind == WalkMode.FLOWERS) flowerStops else roadStops)
                                        },
                                        onRename = ::renameRoute,
                                        onLoad = ::loadRoute,
                                        onExport = { route ->
                                            exportRouteId = route.id
                                            exportGpx.launch(route.fileName())
                                        },
                                        onDelete = ::deleteRoute,
                                        onImport = {
                                            importMode = kind
                                            importGpx.launch(arrayOf(
                                                "application/gpx+xml", "application/xml", "text/xml", "*/*"))
                                        },
                                    )
                                }
                            }
                            ToolTab.ROUTE -> {
                                JourneyControls(
                                    mode = WalkMode.ROUTE,
                                    stops = roadStops,
                                    origin = mockPosition ?: roadStops.firstOrNull() ?: selected,
                                    activeMode = walkMode,
                                    phase = walkPhase,
                                    speedPreset = speedPreset,
                                    onSpeedSelected = ::chooseWalkSpeed,
                                    foreground = foreground, muted = muted,
                                    onUndo = { prefs.saveRouteDraft(roadStops.dropLast(1)) },
                                    onClear = { prefs.saveRouteDraft(emptyList()) },
                                    onMessage = { message = it },
                                    onAskLocation = { requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                )
                            }
                            ToolTab.FLOWERS -> {
                                FlowerMapControls(
                                    start = mockPosition ?: flowerStart,
                                    usingMockStart = mockPosition != null,
                                    selected = flowerPick,
                                    editingStopIndex = editingFlowerStopIndex,
                                    stops = flowerStops,
                                    foreground = foreground, muted = muted,
                                    onAddSelected = {
                                        val chosen = flowerPick
                                        val start = mockPosition ?: flowerStart
                                        when {
                                            chosen == null -> message = "請先在地圖選擇花點"
                                            start == null -> message = "請先在地圖設定規劃起點"
                                            flowerStops.size > FlowerMapDraft.MAX_STOPS ->
                                                message = "舊版路點超過 14 個，請先移除部分路點"
                                            else -> {
                                                val update = FlowerMapDraft.add(
                                                    flowerStops, start, chosen.coordinate
                                                )
                                                message = when (update.result) {
                                                    FlowerMapDraft.AddResult.ADDED -> {
                                                        prefs.saveFlowerDraft(update.stops)
                                                        "已加入第 ${update.stops.size} 個尋花路點"
                                                    }
                                                    FlowerMapDraft.AddResult.DUPLICATE ->
                                                        "附近已有路點，請勿重複加入"
                                                    FlowerMapDraft.AddResult.AT_START ->
                                                        "此位置太接近起點，請選擇其他花點"
                                                    FlowerMapDraft.AddResult.FULL ->
                                                        "最多只能加入 14 個尋花路點"
                                                }
                                            }
                                        }
                                    },
                                    onReplaceStop = {
                                        val index = editingFlowerStopIndex
                                        val chosen = flowerPick
                                        val start = mockPosition ?: flowerStart
                                        when {
                                            index == null -> message = "請先選擇要編輯的路點"
                                            chosen == null -> message = "請先點地圖選擇新位置"
                                            start == null -> message = "請先設定路線起點"
                                            else -> {
                                                val update = FlowerMapDraft.replace(
                                                    flowerStops, start, index, chosen.coordinate,
                                                )
                                                message = when (update.result) {
                                                    FlowerMapDraft.ReplaceResult.REPLACED -> {
                                                        prefs.saveFlowerDraft(update.stops)
                                                        editingFlowerStopIndex = null
                                                        flowerPick = null
                                                        "已更新第 ${index + 1} 個路點；請重新預覽道路"
                                                    }
                                                    FlowerMapDraft.ReplaceResult.UNCHANGED ->
                                                        "與原路點位置相同，未進行修改"
                                                    FlowerMapDraft.ReplaceResult.DUPLICATE ->
                                                        "新位置太靠近其他路點，未進行修改"
                                                    FlowerMapDraft.ReplaceResult.AT_START ->
                                                        "新位置太靠近起點，未進行修改"
                                                    FlowerMapDraft.ReplaceResult.OUT_OF_RANGE ->
                                                        "路點已變動，請重新選擇要編輯的路點"
                                                }
                                            }
                                        }
                                    },
                                    onSelectStop = { index ->
                                        flowerStops.getOrNull(index)?.let { stop ->
                                            selected = stop
                                            flowerPick = null
                                            editingFlowerStopIndex = index
                                            message = "已選中第 ${index + 1} 個路點，可在地圖上重新指定位置"
                                        }
                                    },
                                    onCancelStopEdit = {
                                        editingFlowerStopIndex = null
                                        flowerPick = null
                                    },
                                    onUseAsStart = {
                                        flowerPick?.let {
                                            flowerStart = it.coordinate
                                            message = "已設定規劃起點：${it.coordinate.label()}"
                                        }
                                    },
                                    onDismissSelected = { flowerPick = null },
                                    onMoveStop = { index, direction ->
                                        prefs.saveFlowerDraft(
                                            FlowerMapDraft.move(flowerStops, index, direction)
                                        )
                                    },
                                    onRemoveStop = { index ->
                                        val updated = FlowerMapDraft.remove(flowerStops, index)
                                        if (updated != flowerStops) {
                                            prefs.saveFlowerDraft(updated)
                                            editingFlowerStopIndex = null
                                            flowerPick = null
                                            message = "已移除第 ${index + 1} 個路點；請重新預覽道路"
                                        }
                                    },
                                )
                                JourneyControls(
                                    mode = WalkMode.FLOWERS,
                                    stops = flowerStops,
                                    origin = mockPosition ?: flowerStart,
                                    activeMode = walkMode,
                                    phase = walkPhase,
                                    speedPreset = speedPreset,
                                    onSpeedSelected = ::chooseWalkSpeed,
                                    foreground = foreground, muted = muted,
                                    onUndo = { prefs.saveFlowerDraft(flowerStops.dropLast(1)) },
                                    onClear = { prefs.saveFlowerDraft(emptyList()) },
                                    onMessage = { message = it },
                                    onAskLocation = { requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                    onPreviewRoad = { geometry ->
                                        flowerRoadPreview = geometry
                                        if (geometry.size >= 2) {
                                            mapExplore = true
                                            moveMapCamera(MapCameraGeometry.forRoute(
                                                mockPosition ?: flowerStart, flowerStops, geometry,
                                            ))
                                        }
                                    },
                                )
                                FlowerCatalogPanel(
                                    spots = allFlowers,
                                    origin = mockPosition ?: flowerStart ?: selected,
                                    selected = selected,
                                    draft = flowerStops,
                                    canAddToRoute = mockPosition != null || flowerStart != null,
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
                                        val start = mockPosition ?: flowerStart
                                        if (start == null) message = "請先在地圖設定規劃起點"
                                        else if (flowerStops.size > 14) message = "請先移除超出的舊版路點"
                                        else {
                                            val candidate = FlowerFinder.appendToDraft(
                                                start, flowerStops, listOf(flower), 14
                                            )
                                            if (candidate.size == flowerStops.size) {
                                                message = "此花點已在路線內，或已達花點數量上限"
                                            } else {
                                                prefs.saveFlowerDraft(candidate)
                                                message = "已將「" + flower.name + "」加入尋花路線"
                                            }
                                        }
                                    },
                                    onAppendNearby = { candidates ->
                                        val start = mockPosition ?: flowerStart
                                        if (start == null) message = "請先在地圖設定規劃起點"
                                        else if (flowerStops.size > 14) message = "請先移除超出的舊版路點"
                                        else {
                                            val added = FlowerFinder.appendToDraft(
                                                start, flowerStops, candidates, 14
                                            )
                                            if (added.size == flowerStops.size) {
                                                message = "附近沒有可加入的新花點"
                                            } else {
                                                prefs.saveFlowerDraft(added)
                                                message = "已新增 " + (added.size - flowerStops.size) +
                                                    " 個候選花點；請先預覽道路"
                                            }
                                        }
                                    },
                                    onImport = {
                                        importFlowers.launch(arrayOf("application/json", "application/geo+json", "*/*"))
                                    },
                                    onExport = {
                                        exportFlowers.launch("PikminBee-flowers.geojson")
                                    },
                                )
                                FlowerVisionPanel(
                                    foreground = foreground,
                                    muted = muted,
                                    onMessage = { message = it },
                                )
                            }
                        }
                },
            )
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
