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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.naos.pikminbee.data.PlaceLookup
import com.naos.pikminbee.data.PlaceSuggestion
import com.naos.pikminbee.data.PlaceSuggestionPolicy
import com.naos.pikminbee.data.SavedPlaceMetadataPolicy
import com.naos.pikminbee.flower.FlowerCatalogStore
import com.naos.pikminbee.flower.FlowerJsonCodec
import com.naos.pikminbee.data.MapAppearance
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.data.MapBasemapPolicy
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.PreparedRouteState
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.MockGpsJitterLevel
import com.naos.pikminbee.data.SavedRouteState
import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.gpx.GpxCodec
import com.naos.pikminbee.gpx.GpxImport
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.MapCameraGeometry
import com.naos.pikminbee.domain.RoutePointDraft
import com.naos.pikminbee.domain.PreparedWalkingRoute
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.route.ValhallaPlanner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.location.OverlayMapSelection
import com.naos.pikminbee.location.CheckpointStatus
import com.naos.pikminbee.location.SessionJournal
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.ui.LocationMap
import com.naos.pikminbee.ui.VerifiedPhysicalFixState
import com.naos.pikminbee.ui.rememberDeviceLocation
import com.naos.pikminbee.ui.MapCameraRequest
import com.naos.pikminbee.ui.MapViewport
import com.naos.pikminbee.ui.JourneyControls
import com.naos.pikminbee.ui.RouteLibraryPanel
import com.naos.pikminbee.ui.FavouritesPanel
import com.naos.pikminbee.ui.WifiControlPanel
import com.naos.pikminbee.ui.BackgroundSessionPanel
import com.naos.pikminbee.ui.MapFirstShell
import com.naos.pikminbee.ui.WalkStopUiPolicy
import com.naos.pikminbee.ui.BeeSettingsScreen
import com.naos.pikminbee.ui.BeePermissionWizard
import com.naos.pikminbee.ui.PikminBeeThemeColors
import com.naos.pikminbee.ui.NaosThemeFamily
import com.naos.pikminbee.ui.NaosGlassMaterial
import com.naos.pikminbee.ui.NaosActionIcon
import com.naos.pikminbee.ui.NaosActionGlyph
import com.naos.pikminbee.ui.NaosModeIcon
import com.naos.pikminbee.ui.NaosVisualState
import com.naos.pikminbee.ui.NaosDesignSystem
import com.naos.pikminbee.ui.OverlayGuideDialog
import com.naos.pikminbee.ui.LocalNaosVisualState
import com.naos.pikminbee.ui.BeePermissions
import kotlinx.coroutines.flow.MutableStateFlow

private val navy = Color(0xFF18263A)
private val honey = Color(0xFF9F5E0C)
private val lightGlass = Color.White.copy(alpha = 0.975f)
private val darkGlass = Color(0xFF1E2B3C).copy(alpha = 0.985f)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.naos.pikminbee.OPEN_SETTINGS"
        const val EXTRA_OPEN_MODE = "com.naos.pikminbee.OPEN_MODE"
        val settingsRequests = MutableStateFlow(0L)
        val mapModeRequests = MutableStateFlow(-1)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false))
            settingsRequests.value++
        val selectedMode = intent.getIntExtra(EXTRA_OPEN_MODE, -1)
        if (selectedMode in 0..3) mapModeRequests.value = selectedMode
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PikminBeeScreen() }
    }
}

private enum class BeePage { MAP, SETTINGS, PERMISSIONS }

private enum class ToolTab(val label: String) {
    TELEPORT("瞬移"), ROUTE("路徑")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PikminBeeScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context) }
    val placeLookup = remember(context) { PlaceLookup(context) }
    val flowerCatalog = remember(context) { FlowerCatalogStore(context) }
    remember(context) { SessionJournal(context).also { it.read() } }
    val checkpoint by CheckpointStatus.last.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var provider by remember { mutableStateOf(prefs.provider()) }
    var appearance by remember { mutableStateOf(prefs.appearance()) }
    val naosFamily = NaosThemeFamily.MINERAL
    val naosMaterial = NaosGlassMaterial.REGULAR
    val dark = when (appearance) {
        MapAppearance.SYSTEM -> systemDark
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
    val semantic = NaosDesignSystem.palette(naosFamily, dark)
    val foreground = semantic.ink
    val muted = semantic.muted

    var selected by remember { mutableStateOf(Coordinate(25.0330, 121.5654)) }
    var selectionExplicit by remember { mutableStateOf(false) }
    val preparedRoute by PreparedRouteState.route.collectAsState()
    val routeRoadPreview = preparedRoute?.geometry.orEmpty()
    var routeEditingStopIndex by remember { mutableStateOf<Int?>(null) }
    var routeStopActionIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTeleport by remember { mutableStateOf<Coordinate?>(null) }
    var mapExplore by remember { mutableStateOf(false) }
    var cameraSerial by remember { mutableIntStateOf(0) }
    var cameraRequest by remember { mutableStateOf<MapCameraRequest?>(null) }
    var mapViewport by remember { mutableStateOf<MapViewport?>(null) }
    var initiallyFocusedOnPhone by remember { mutableStateOf(false) }
    val controlsScroll = rememberScrollState()
    var latitudeText by remember { mutableStateOf(selected.latitude.toString()) }
    var longitudeText by remember { mutableStateOf(selected.longitude.toString()) }
    var tab by remember {
        val requestedMode = (context as? MainActivity)?.intent
            ?.getIntExtra(MainActivity.EXTRA_OPEN_MODE, -1) ?: -1
        mutableStateOf(
            when {
                requestedMode in 0..1 -> ToolTab.entries[requestedMode]
                requestedMode == 2 -> ToolTab.ROUTE
                else -> ToolTab.TELEPORT
            },
        )
    }
    LaunchedEffect(tab) { controlsScroll.scrollTo(0) }
    var showWifiPanel by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(prefs.places()) }
    var showFavourites by remember { mutableStateOf(false) }
    var backfillingPlaces by remember { mutableStateOf(false) }
    var backfillResult by remember { mutableStateOf("") }
    var savingPlace by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("地圖點一下就能選擇位置") }
    val roadStops by RouteDraftState.routeStops.collectAsState()
    val savedRoutes by SavedRouteState.routes.collectAsState()
    var planning by remember { mutableStateOf(false) }
    var confirmStopWalk by remember { mutableStateOf(false) }
    val speedPreset by WalkSpeedState.selected.collectAsState()
    val speedProfile by WalkSpeedState.profile.collectAsState()
    val defaultSpeedPreset by WalkSpeedState.preferred.collectAsState()
    var mockGpsJitterLevel by remember { mutableStateOf(prefs.mockGpsJitterLevel()) }
    var virtualStrideMetres by remember { mutableStateOf(prefs.virtualStrideMetres()) }
    val work = rememberCoroutineScope()
    val mockStatus by LocationStatus.display.collectAsState()
    val mockPosition by LocationStatus.selected.collectAsState()
    val walkPhase by LocationStatus.phase.collectAsState()
    val walkMode by LocationStatus.mode.collectAsState()
    val roadLine by LocationStatus.geometry.collectAsState()
    val walkingProgress by LocationStatus.progress.collectAsState()
    val walkEstimatedSteps by LocationStatus.walkEstimatedSteps.collectAsState()
    val overlayVisible by LocationStatus.overlayVisible.collectAsState()
    LaunchedEffect(walkPhase) {
        if (!WalkStopUiPolicy.canRequest(walkMode, walkPhase))
            confirmStopWalk = false
    }
    // Draft edits invalidate their previous preview.
    LaunchedEffect(roadStops) { PreparedRouteState.route.value = null }

    var phoneLocationRefresh by remember { mutableIntStateOf(0) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        phoneLocationRefresh++
        val pending = pendingTeleport
        pendingTeleport = null
        if (granted && pending != null) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, MockLocationService.teleportIntent(context, pending))
            }.onSuccess {
                cameraSerial++
                cameraRequest = MapCameraRequest(
                    cameraSerial, MapCameraGeometry.fitPoints(listOf(pending)))
                message = "已要求瞬移至 ${pending.label()}"
            }.onFailure { message = "無法啟動瞬移：${it.message}" }
        } else {
            message = if (granted) "定位授權已取得" else "請先授予精確定位權限"
        }
    }
    val phonePosition by rememberDeviceLocation(phoneLocationRefresh)
    LaunchedEffect(selected, selectionExplicit) {
        if (selectionExplicit) OverlayMapSelection.picked.value = selected
    }
    LaunchedEffect(phonePosition) {
        OverlayMapSelection.deviceFix.value = phonePosition
        OverlayMapSelection.deviceFixTimestampMillis.value =
            if (phonePosition != null) System.currentTimeMillis() else 0L
    }
    val overlaySettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            runCatching {
                context.startService(MockLocationService.command(
                    context, MockLocationService.ACTION_SHOW_OVERLAY))
            }.onFailure { message = "無法開啟懸浮蜜蜂：${it.message}" }
            message = "懸浮權限已授予，正在顯示蜜蜂控制"
        } else {
            message = "尚未授權顯示在其他應用程式上層，可稍後再開啟"
        }
    }

    fun requestOverlayEnabled() {
        if (!Settings.canDrawOverlays(context)) {
            message = "需要「顯示在其他應用程式上層」權限，返回後將自動開啟"
            val overlayPermission = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            runCatching { overlaySettings.launch(overlayPermission) }.onFailure {
                message = "無法開啟系統懸浮權限頁面，請由應用程式設定授權"
            }
        } else runCatching {
            context.startService(MockLocationService.command(context,
                MockLocationService.ACTION_SHOW_OVERLAY))
            message = "正在顯示極簡蜜蜂控制，點圖示可展開"
        }.onFailure { message = "無法開啟懸浮蜜蜂：${it.message}" }
    }

    var importMode by remember { mutableStateOf(WalkMode.ROUTE) }
    var exportRouteId by remember { mutableStateOf<String?>(null) }
    var pendingSimplifiedGpx by remember { mutableStateOf<GpxImport?>(null) }

    fun acceptImportedGpx(imported: GpxImport) {
        runCatching {
            // Legacy flower-tagged GPX remains usable as an ordinary route.
            val route = prefs.addSavedRoute(imported.name, WalkMode.ROUTE, imported.stops)
            prefs.loadSavedRouteIntoDraft(route)
            tab = ToolTab.ROUTE
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
        if (route.mode == WalkMode.FLOWERS) {
            // Migrate only the *draft*, never mutate/delete the user's saved legacy route.
            prefs.saveRouteDraft(route.stops)
            tab = ToolTab.ROUTE
            message = "舊尋花收藏已載入一般路徑草稿；原收藏保留，請重新預覽道路"
            return
        }
        prefs.loadSavedRouteIntoDraft(route)
        tab = ToolTab.ROUTE
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

    val activeRouteWalk = walkMode == WalkMode.ROUTE &&
        walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
    val routePreviewReady = preparedRoute?.isCurrent(
        mockPosition ?: phonePosition, roadStops
    ) == true
    val displayedRoad = if (tab == ToolTab.ROUTE) {
        if (activeRouteWalk) roadLine
        else if (routePreviewReady) routeRoadPreview else emptyList()
    } else emptyList()
    fun chooseSpeedProfile(next: WalkSpeedProfile) {
        prefs.setWalkSpeedProfile(next)
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
    fun setMockGpsJitter(next: MockGpsJitterLevel) {
        prefs.setMockGpsJitterLevel(next)
        mockGpsJitterLevel = next
        message = if (next == MockGpsJitterLevel.OFF)
            "模擬定位微幅漂移已關閉"
        else "模擬定位微幅漂移設定為 ${next.metres} 公尺（僅影響回報座標）"
    }
    fun moveMapCamera(target: MapCameraGeometry.ViewTarget) {
        if (!target.hasAny) return
        cameraSerial++
        cameraRequest = MapCameraRequest(cameraSerial, target)
        if (target.crossesDateLine) {
            message = "路線跨越國際換日線，暫先聚焦起點；可手動縮放地圖"
        }
    }
    // Focus the first available actual fix once; do not chase GPS updates
    // or interrupt subsequent user-driven map exploration.
    LaunchedEffect(mockPosition, phonePosition) {
        val firstFix = mockPosition ?: phonePosition
        if (!initiallyFocusedOnPhone && firstFix != null) {
            initiallyFocusedOnPhone = true
            moveMapCamera(MapCameraGeometry.fitPoints(listOf(firstFix)))
        }
    }
    fun fitCurrentRoute() {
        val target = if (tab == ToolTab.ROUTE)
            MapCameraGeometry.forRoute(mockPosition ?: phonePosition, roadStops, displayedRoad)
        else MapCameraGeometry.fitPoints(listOf(selected))
        mapExplore = true
        moveMapCamera(if (target.hasAny) target
            else MapCameraGeometry.fitPoints(listOf(selected)))
    }
    fun focusMapPoint(point: Coordinate) {
        mapExplore = true
        moveMapCamera(MapCameraGeometry.fitPoints(listOf(point)))
    }

    var activePage by remember {
        mutableStateOf(if ((context as? MainActivity)?.intent?.let {
                it.getBooleanExtra(MainActivity.EXTRA_OPEN_SETTINGS, false) ||
                    it.getIntExtra(MainActivity.EXTRA_OPEN_MODE, -1) == 3
            } == true) BeePage.SETTINGS
            else if (prefs.setupCompleted()) BeePage.MAP else BeePage.PERMISSIONS)
    }
    val settingsRequest by MainActivity.settingsRequests.collectAsState()
    val modeRequest by MainActivity.mapModeRequests.collectAsState()
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showOverlayAfterGuide by remember { mutableStateOf(false) }
    var openDetailsRequest by remember { mutableIntStateOf(0) }
    var closeDetailsRequest by remember { mutableIntStateOf(0) }
    var guideEnabled by remember { mutableStateOf(prefs.overlayFirstUseGuideEnabled()) }
    var holdHints by remember { mutableStateOf(prefs.overlayLongPressTips()) }
    fun toggleOverlayControl() {
        when {
            overlayVisible -> runCatching {
                context.startService(MockLocationService.command(
                    context, MockLocationService.ACTION_HIDE_OVERLAY))
                message = "正在關閉懸浮蜜蜂（不停止行走）"
            }.onFailure { message = "無法關閉懸浮蜜蜂：${it.message}" }
            guideEnabled && !prefs.overlayGuideSeen() -> {
                showOverlayAfterGuide = true
                showOverlayGuide = true
            }
            else -> requestOverlayEnabled()
        }
    }

    var confirmStartMode by remember { mutableStateOf<WalkMode?>(null) }

    fun startPreparedWalk(mode: WalkMode) {
        if (mode != WalkMode.ROUTE) {
            message = "尋花模式已移除；請使用一般路徑"
            return
        }
        val ready = PreparedRouteState.get(WalkMode.ROUTE)
        val start = mockPosition ?: phonePosition
        val stops = roadStops
        val loop = false
        if (ready == null || !ready.isCurrent(start, stops, loop)) {
            message = "路點或起點已變更，請先重新規劃"
            return
        }
        when {
            ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                message = "開始行走前請授予精確定位權限"
                requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            !BeePermissions.inspect(context).selectedMockApp ->
                message = "請先將 PikminBee 設為模擬位置應用程式"
            else -> runCatching {
                ContextCompat.startForegroundService(context,
                    MockLocationService.routeIntent(context, ready.geometry,
                        WalkSpeedState.profile.value.nominalMps, mode, loop))
            }.onSuccess {
                message = "已使用預覽道路開始路徑行走"
            }.onFailure {
                message = "無法開始行走：" + (it.message ?: "請檢查系統權限")
            }
        }
    }

    /** One preview → confirm → start/pause/resume command for both main modes. */
    fun requestStopWalk() {
        if (WalkStopUiPolicy.canRequest(walkMode, walkPhase))
            confirmStopWalk = true
    }

    fun handleJourneyPrimary() {
        if (walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
            runCatching {
                context.startService(MockLocationService.command(context,
                    if (walkPhase == WalkPhase.PAUSED)
                        MockLocationService.ACTION_RESUME_ROUTE
                    else MockLocationService.ACTION_PAUSE_ROUTE))
            }.onFailure { message = "無法切換行走狀態：${it.message}" }
            return
        }
        if (tab != ToolTab.ROUTE) {
            message = "請先切換到路徑模式"
            return
        }
        val mode = WalkMode.ROUTE
        val origin = mockPosition ?: phonePosition
        val stops = roadStops
        val loop = false
        val ready = PreparedRouteState.get(mode)
        when {
            planning -> Unit
            origin == null -> message = "請先取得定位或在地圖設定起點"
            stops.isEmpty() -> message = "請先在地圖選取至少一個路點"
            ready?.isCurrent(origin, stops, loop) == true -> {
                if (ready.requiresSnapConfirmation) confirmStartMode = mode
                else startPreparedWalk(mode)
            }
            else -> {
                val capturedStops = stops.toList()
                val capturedOrigin = origin
                val capturedLoop = loop
                planning = true
                message = "正在規劃可步行道路…"
                work.launch {
                    try {
                        val plan = ValhallaPlanner.planDetailed(listOf(capturedOrigin) + capturedStops)
                        val currentOrigin = mockPosition ?: phonePosition
                        if (currentOrigin == null || roadStops != capturedStops ||
                            WalkingGeometry.distanceMeters(capturedOrigin, currentOrigin) > 30.0) {
                            message = "規劃期間路點或起點已變更，請重新規劃"
                        } else {
                            PreparedRouteState.set(mode, PreparedWalkingRoute(
                                origin = capturedOrigin,
                                stops = capturedStops,
                                geometry = plan.geometry,
                                loop = capturedLoop,
                                snapMetres = plan.snapMetres,
                            ))
                            mapExplore = true
                            moveMapCamera(MapCameraGeometry.forRoute(
                                capturedOrigin, capturedStops, plan.geometry))
                            closeDetailsRequest++
                            message = if (plan.maxSnapMetres > 40.0)
                                "路線已預覽，最近道路最大偏移 ${plan.maxSnapMetres.toInt()} 公尺，開始前須確認"
                            else "路線已預覽，再按一次即可開始行走"
                        }
                    } catch (error: Exception) {
                        message = "道路規劃失敗：" + (error.message ?: "請檢查網路或路點")
                    } finally {
                        planning = false
                    }
                }
            }
        }
    }

    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0L) activePage = BeePage.SETTINGS
    }
    LaunchedEffect(modeRequest) {
        if (modeRequest == 3) {
            activePage = BeePage.SETTINGS
            MainActivity.mapModeRequests.value = -1
        } else if (modeRequest in 0..2) {
            tab = if (modeRequest == 2) ToolTab.ROUTE else ToolTab.entries[modeRequest]
            activePage = BeePage.MAP
            routeEditingStopIndex = null
            routeStopActionIndex = null
            closeDetailsRequest++
            MainActivity.mapModeRequests.value = -1
        }
    }

    var coordinateInput by remember { mutableStateOf("") }
    var coordinateInputDismissToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected, selectionExplicit) {
        coordinateInput = if (selectionExplicit) selected.label() else ""
    }
    fun saveSelectedPlace() {
        if (savingPlace) return
        if (!selectionExplicit) {
            message = "請先點地圖選取要收藏的位置"
        } else {
            val target = selected
            val alreadySaved = prefs.places().firstOrNull {
                it.coordinate == target
            }
            if (alreadySaved != null) {
                message = "這個位置已收藏為「${alreadySaved.name}」；可在收藏清單改名"
            } else {
                savingPlace = true
                message = "正在取得附近地標／商家名稱與國家、時區後收藏…"
                // Only this explicit tap sends the selected
                // coordinate to Photon / TimeAPI, not map pans.
                work.launch {
                    try {
                        val suggestion = runCatching {
                            placeLookup.suggest(target)
                        }.getOrElse {
                            PlaceSuggestion(PlaceSuggestionPolicy.pointLabel(target))
                        }
                        // A paired browser may have saved the same
                        // coordinate while the lookup was running.
                        if (prefs.places().none { it.coordinate == target }) {
                            prefs.addPlace(
                                suggestion.displayName, target,
                                suggestion.countryCode,
                                suggestion.countryName,
                                suggestion.city,
                                suggestion.timeZoneId,
                            )
                        }
                        places = prefs.places()
                        message = if (suggestion.displayName ==
                            PlaceSuggestionPolicy.pointLabel(target))
                            "地名查詢無結果，已用座標名稱收藏；可稍後補齊資訊"
                        else "已自動命名並收藏「${suggestion.displayName}」 " +
                            listOf(suggestion.city, suggestion.countryName ?: suggestion.countryCode)
                                .filterNot { it.isNullOrBlank() }
                                .joinToString(" · ")
                    } catch (e: Exception) {
                        message = "收藏失敗：${e.message ?: "請再試一次"}"
                    } finally {
                        savingPlace = false
                    }
                }
            }
        }
    }
    fun teleportTo(coordinate: Coordinate) {
        selected = coordinate
        selectionExplicit = true
        tab = ToolTab.TELEPORT
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTeleport = coordinate
            message = "請先授予精確定位權限，授權後才會執行瞬移"
            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context, MockLocationService.teleportIntent(context, coordinate))
        }.onSuccess {
            focusMapPoint(coordinate)
            message = "已要求瞬移至 ${coordinate.label()}"
        }.onFailure {
            message = "瞬移失敗：${it.message}"
        }
    }
    fun applyCoordinateInput() {
        val parsed = Coordinate.parseCombined(coordinateInput)
        if (parsed == null) {
            message = "經緯度格式或範圍無效（支援 DD、DMS、DDM）"
            return
        }
        teleportTo(parsed)
    }
    fun goToCurrentMapPosition() {
        val virtual = mockPosition
        if (virtual != null) {
            focusMapPoint(virtual)
            message = "已移至目前模擬位置，沒有瞬移"
            return
        }
        if (phonePosition != null) {
            focusMapPoint(phonePosition!!)
            message = "已移至手機目前位置，沒有瞬移"
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

    MaterialTheme(colorScheme = PikminBeeThemeColors.forDarkMode(dark).copy(
        primary = semantic.accent, onPrimary = semantic.onAccent,
        background = semantic.bg, onBackground = semantic.ink,
        surface = semantic.surface, onSurface = semantic.ink,
        surfaceVariant = semantic.soft, onSurfaceVariant = semantic.muted,
        outline = semantic.stroke,
    )) {
        CompositionLocalProvider(
            LocalNaosVisualState provides NaosVisualState(naosFamily, naosMaterial, dark)
        ) {
        if (confirmStopWalk) {
            AlertDialog(
                onDismissRequest = { confirmStopWalk = false },
                title = { Text("停止目前的路線？") },
                text = {
                    Text("停止後模擬 GPS 會停留在目前位置，不會自動恢復真實定位。若只是暫時停下，可改用「暫停」。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmStopWalk = false
                        if (WalkStopUiPolicy.canRequest(walkMode, walkPhase)) {
                            runCatching {
                                context.startService(MockLocationService.command(
                                    context, MockLocationService.ACTION_STOP_ROUTE))
                            }.onSuccess { message = "已要求停止路線，請確認手機定位狀態" }
                                .onFailure { message = "無法停止路線：${it.message ?: "請檢查系統狀態"}" }
                        }
                    }) { Text("確認停止") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStopWalk = false }) {
                        Text("保留目前狀態")
                    }
                },
            )
        }
        confirmStartMode?.let { mode ->
            val ready = PreparedRouteState.get(mode)
            AlertDialog(
                onDismissRequest = { confirmStartMode = null },
                title = { Text("確認道路吸附位置") },
                text = {
                    Text("所選路點距離最近可步行道路最多約 " +
                        "${ready?.maxSnapMetres?.toInt() ?: 0} 公尺。" +
                        " 起點約偏移 ${ready?.originSnapMetres?.toInt() ?: 0} 公尺。" +
                        " 開始後會先移動到吸附的道路位置，請確認地圖上的預覽路線。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmStartMode = null
                        startPreparedWalk(mode)
                    }) { Text("確認並開始") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStartMode = null }) { Text("返回預覽") }
                },
            )
        }
        if (showOverlayGuide) {
            OverlayGuideDialog(onFinished = {
                prefs.markOverlayGuideSeen()
                showOverlayGuide = false
                if (showOverlayAfterGuide) {
                    showOverlayAfterGuide = false
                    requestOverlayEnabled()
                }
            })
        }
        if (activePage == BeePage.MAP && tab == ToolTab.ROUTE) {
            routeStopActionIndex?.let { index ->
                AlertDialog(
                    onDismissRequest = { routeStopActionIndex = null },
                    title = { Text("第 ${index + 1} 點") },
                    text = { Text("重新選擇位置，或從路徑中刪除此點。其餘路點會重新依序編號。") },
                    confirmButton = {
                        TextButton(onClick = {
                            routeStopActionIndex = null
                            if (index in roadStops.indices &&
                                walkPhase !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
                                routeEditingStopIndex = index
                                message = "請點地圖重新選擇第 ${index + 1} 點"
                            } else message = "行走中不能修改路點，請先停止"
                        }) { Text("重新選點") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { routeStopActionIndex = null }) { Text("取消") }
                            TextButton(onClick = {
                                routeStopActionIndex = null
                                if (walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
                                    message = "行走中不能刪除路點，請先停止"
                                } else {
                                    prefs.saveRouteDraft(RoutePointDraft.remove(roadStops, index))
                                    routeEditingStopIndex = null
                                    message = "已刪除第 ${index + 1} 點；其餘路點已重新編號"
                                }
                            }) { Text("刪除") }
                        }
                    },
                )
            }
        }
        when (activePage) {
            BeePage.PERMISSIONS -> BeePermissionWizard(
                dark = dark,
                initial = !prefs.setupCompleted(),
                onFinish = {
                    prefs.setSetupCompleted(true)
                    phoneLocationRefresh++
                    activePage = BeePage.MAP
                },
                onExit = { activePage = BeePage.MAP },
            )
            BeePage.SETTINGS -> BeeSettingsScreen(
                dark = dark,
                provider = provider,
                appearance = appearance,
                defaultSpeed = defaultSpeedPreset,
                virtualStrideMetres = virtualStrideMetres,
                mockGpsJitterLevel = mockGpsJitterLevel,
                overlayVisible = overlayVisible,
                mockActive = mockPosition != null,
                showWifiPanel = showWifiPanel,
                checkpoint = checkpoint,
                onProvider = {
                    provider = it
                    prefs.setProvider(it)
                },
                onAppearance = {
                    appearance = it
                    prefs.setAppearance(it)
                    if (overlayVisible) runCatching {
                        context.startService(MockLocationService.command(
                            context, MockLocationService.ACTION_REFRESH_OVERLAY_APPEARANCE))
                    }.onFailure { message = "已切換主題，浮窗更新失敗：${it.message}" }
                },
                onDefaultSpeed = ::setDefaultWalkSpeed,
                onVirtualStrideMetres = {
                    prefs.setVirtualStrideMetres(it)
                    virtualStrideMetres = it
                    message = "虛擬步幅已設定為 ${String.format(java.util.Locale.US, "%.1f", it * 100).removeSuffix(".0")} 公分，下一次行走生效"
                },
                onMockGpsJitterLevel = ::setMockGpsJitter,
                onPermissions = { activePage = BeePage.PERMISSIONS },
                overlayGuideEnabled = guideEnabled,
                overlayHoldHints = holdHints,
                onOverlayGuideEnabled = {
                    guideEnabled = it
                    prefs.setOverlayFirstUseGuideEnabled(it)
                },
                onOverlayHoldHints = {
                    holdHints = it
                    prefs.setOverlayLongPressTips(it)
                },
                onReplayOverlayGuide = {
                    showOverlayAfterGuide = false
                    showOverlayGuide = true
                },
                onOverlay = { toggleOverlayControl() },
                onToggleWifi = { showWifiPanel = !showWifiPanel },
                onAskLocation = {
                    requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onMessage = { message = it },
                statusMessage = message,
                onBack = { activePage = BeePage.MAP },
            )
            BeePage.MAP -> MapFirstShell(
                dark = dark,
                modeIndex = tab.ordinal,
                modeTitle = tab.label,
                message = if (tab == ToolTab.ROUTE)
                    routeEditingStopIndex?.let { "請點地圖重選第 ${it + 1} 點" }
                        ?: if (roadStops.size >= RoutePointDraft.MAX_STOPS)
                            "已選滿 ${RoutePointDraft.MAX_STOPS} 個路點 · 上滑可復原或刪除"
                        else "下一點：第 ${roadStops.size + 1} 點 · 上滑復原或管理路點"
                    else message,
                coordinateInput = coordinateInput,
                coordinateInputDismissToken = coordinateInputDismissToken,
                onCoordinateChange = { coordinateInput = it },
                onCoordinateTeleport = ::applyCoordinateInput,
                onCurrentLocation = {
                    coordinateInputDismissToken++
                    goToCurrentMapPosition()
                },
                onHome = {
                    activePage = BeePage.MAP
                    mapExplore = false
                    goToCurrentMapPosition()
                },
                onFavourites = { showFavourites = true; coordinateInputDismissToken++ },
                openDetailsRequest = openDetailsRequest,
                closeDetailsRequest = closeDetailsRequest,
                onMode = { index ->
                    coordinateInputDismissToken++
                    if (index !in 0..1) activePage = BeePage.SETTINGS
                    else tab = ToolTab.entries[index]
                    routeEditingStopIndex = null
                    routeStopActionIndex = null
                    mapExplore = false
                },
                onSettings = { activePage = BeePage.SETTINGS },
                overlayVisible = overlayVisible,
                onToggleOverlay = { toggleOverlayControl() },
                onPermissions = { activePage = BeePage.PERMISSIONS },
                mapExplore = mapExplore,
                onCloseExplore = { mapExplore = false },
                routeStopsCount = roadStops.size,
                walkingProgress = walkingProgress,
                walkEstimatedSteps = walkEstimatedSteps,
                walkStopped = walkPhase == WalkPhase.PARKED &&
                    tab == ToolTab.ROUTE && walkMode == WalkMode.ROUTE,
                routeSpeedKmh = speedPreset.kilometresPerHour,
                routeActive = walkMode == WalkMode.ROUTE &&
                    walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED),
                routePaused = walkPhase == WalkPhase.PAUSED,
                routePreviewReady = routePreviewReady,
                routeSnapMetres = preparedRoute?.maxSnapMetres ?: 0.0,
                routePlanning = planning,
                onRouteUndo = {
                    if (walkPhase !in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
                        when (tab) {
                            ToolTab.ROUTE -> {
                                prefs.saveRouteDraft(RoutePointDraft.undo(roadStops))
                                routeEditingStopIndex = null
                            }
                            else -> Unit
                        }
                        message = "已移除最後一個路點，請點地圖重新選取"
                    }
                },
                onRoutePrimary = { handleJourneyPrimary() },
                onRouteStop = ::requestStopWalk,
                mapContent = {
            LocationMap(
                provider = provider, dark = dark,
                selected = if (selectionExplicit) selected
                    else mockPosition ?: phonePosition ?: selected,
                selectionActive = selectionExplicit,
                current = mockPosition ?: phonePosition,
                currentIsMock = mockPosition != null,
                start = null,
                stops = if (tab == ToolTab.ROUTE) roadStops else emptyList(),
                road = displayedRoad,
                previewRoad = tab == ToolTab.ROUTE && !activeRouteWalk && routePreviewReady,
                cameraRequest = cameraRequest,
                viewport = mapViewport,
                onViewport = { mapViewport = it },
                onCameraApplied = { id ->
                    if (cameraRequest?.id == id) cameraRequest = null
                },
                mapExplore = mapExplore,
                onCameraFailure = { message = it },
                flowerSpots = emptyList(),
                onPick = { point ->
                    coordinateInputDismissToken++
                    selectionExplicit = true
                    if (mapExplore) mapExplore = false
                    when (tab) {
                        ToolTab.ROUTE -> {
                            selected = point
                            if (walkPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
                                message = "行走中不可修改路點，請先停止行走"
                            } else {
                                val edit = RoutePointDraft.select(
                                    roadStops, mockPosition ?: phonePosition, point,
                                    routeEditingStopIndex,
                                )
                                when (edit.outcome) {
                                    RoutePointDraft.Outcome.ADDED -> {
                                        prefs.saveRouteDraft(edit.stops)
                                        message = "已加入第 ${edit.index + 1} 點，請選第 ${edit.stops.size + 1} 點"
                                    }
                                    RoutePointDraft.Outcome.REPLACED -> {
                                        prefs.saveRouteDraft(edit.stops)
                                        routeEditingStopIndex = null
                                        message = "已重新選擇第 ${edit.index + 1} 點"
                                    }
                                    RoutePointDraft.Outcome.DUPLICATE ->
                                        message = "該位置已經是路點，請選擇其他位置"
                                    RoutePointDraft.Outcome.TOO_CLOSE_TO_ORIGIN ->
                                        message = "此位置太接近目前起點，請選擇其他位置"
                                    RoutePointDraft.Outcome.FULL ->
                                        message = "最多 ${RoutePointDraft.MAX_STOPS} 個目的地"
                                    RoutePointDraft.Outcome.INVALID_INDEX -> {
                                        routeEditingStopIndex = null
                                        message = "路點已變更，請重新點選"
                                    }
                                }
                            }
                        }
                        else -> {
                            selected = point
                            message = "已選位置：${point.label()}"
                        }
                    }
                },
                onFlowerPick = { _ -> Unit },
                onStopPick = when (tab) {
                    ToolTab.ROUTE -> { index: Int ->
                        coordinateInputDismissToken++
                        if (index in roadStops.indices) {
                            routeStopActionIndex = index
                            message = "已點選第 ${index + 1} 點，可刪除或重新選位置"
                        }
                    }
                    else -> null
                },
                modifier = Modifier.fillMaxSize()
            )
                },
                modeContent = {
                    Text(
                        when {
                            mockPosition != null -> "🐝 目前模擬位置 · ${mockPosition!!.label()}"
                            phonePosition != null -> "● 手機目前位置 · ${phonePosition!!.label()}"
                            else -> "● 目前位置尚未取得，請確認 Android 定位權限"
                        },
                        color = muted, fontSize = 12.sp,
                    )
                    if (tab == ToolTab.ROUTE) {
                        Text(
                            routeEditingStopIndex?.let {
                                "點地圖重新選擇第 ${it + 1} 點；已存在的路點會保持原順序"
                            } ?: "直接點地圖就新增第 ${roadStops.size + 1} 點，不需要再按新增",
                            color = foreground, fontWeight = FontWeight.SemiBold,
                        )
                    }
                        when (tab) {
                            ToolTab.TELEPORT -> {
                                Text("瞬移到指定位置", fontWeight = FontWeight.Bold, color = foreground)
                                Text("選取：" + selected.label(), color = muted, fontSize = 13.sp)
                                Text("上方座標列可貼上 DD、DMS、DDM；點地圖只會選取，不會自動瞬移。",
                                    color = muted, fontSize = 12.sp)
                                Text("例如 25.033, 121.5654；25°01′58.8″N 121°33′55.44″E；25°1.980′N 121°33.924′E。",
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
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            NaosActionIcon(NaosActionGlyph.VIRTUAL_GPS, Color.White)
                                            Text("瞬移並停留")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { context.startService(MockLocationService.stopIntent(context)) }
                                            message = "正在恢復真實定位"
                                        },
                                        enabled = mockPosition != null,
                                        modifier = Modifier.weight(.9f).height(52.dp),
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            NaosActionIcon(NaosActionGlyph.REAL_GPS, foreground)
                                            Text("恢復真實 GPS")
                                        }
                                    }
                                }
                                if (mockPosition != null) {
                                    Text("模擬座標：${mockPosition!!.label()}", fontSize = 12.sp, color = muted)
                                }
                                TextButton(
                                    enabled = !savingPlace,
                                    onClick = ::saveSelectedPlace
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        NaosModeIcon(3, foreground)
                                        Text(if (savingPlace) "正在自動命名…" else "一鍵收藏")
                                    }
                                }
                            }
                            ToolTab.ROUTE -> {
                                JourneyControls(
                                    mode = WalkMode.ROUTE,
                                    stops = roadStops,
                                    origin = mockPosition ?: phonePosition,
                                    activeMode = walkMode,
                                    phase = walkPhase,
                                    walkEstimatedSteps = walkEstimatedSteps,
                                    speedPreset = speedPreset,
                                    speedProfile = speedProfile,
                                    onSpeedSelected = ::chooseSpeedProfile,
                                    foreground = foreground, muted = muted,
                                    onUndo = {
                                        if (roadStops.isNotEmpty()) {
                                            prefs.saveRouteDraft(RoutePointDraft.undo(roadStops))
                                            routeEditingStopIndex = null
                                            message = "已回到第 ${roadStops.size} 點，請點地圖重選"
                                        }
                                    },
                                    onClear = {
                                        prefs.saveRouteDraft(emptyList())
                                        routeEditingStopIndex = null
                                    },
                                    onMessage = { message = it },
                                    preparedRoad = if (routePreviewReady) routeRoadPreview else emptyList(),
                                    routePlanning = planning,
                                    snapMetres = preparedRoute?.maxSnapMetres ?: 0.0,
                                    loop = false,
                                    onLoopChange = {},
                                    onRoutePrimary = { handleJourneyPrimary() },
                                    onRouteStop = ::requestStopWalk,
                                )
                            }
                        }
                },
            )
        }
        if (showFavourites && activePage == BeePage.MAP) {
            ModalBottomSheet(
                onDismissRequest = { showFavourites = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                containerColor = semantic.surface,
                contentColor = foreground,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            ) {
                FavouritesPanel(
                    places = places,
                    routeCount = savedRoutes.size,
                    foreground = foreground,
                    muted = muted,
                    canAddPlace = selectionExplicit,
                    savingPlace = savingPlace,
                    onAddPlace = ::saveSelectedPlace,
                    onSelectPlace = { place ->
                        showFavourites = false
                        selected = place.coordinate
                        selectionExplicit = true
                        tab = ToolTab.TELEPORT
                        focusMapPoint(place.coordinate)
                        message = "已在地圖選取「${place.name}」，尚未瞬移"
                    },
                    onRenamePlace = { place, name ->
                        if (prefs.renamePlace(place.id, name)) {
                            places = prefs.places()
                            message = "已更新收藏名稱，不會移動 GPS"
                        } else message = "收藏名稱無效或地點不存在"
                    },
                    onDeletePlace = { place ->
                        prefs.removePlace(place.id)
                        places = prefs.places()
                        message = "已移除收藏「${place.name}」"
                    },
                    backfillBusy = backfillingPlaces,
                    backfillMessage = backfillResult,
                    onBackfill = {
                        if (!backfillingPlaces) {
                            backfillingPlaces = true
                            backfillResult = "正在查詢舊收藏缺少的國家、城市及時區…"
                            work.launch {
                                var improved = 0
                                var attempted = 0
                                try {
                                    // Bounded, explicit batch: never poll Photon on GPS ticks.
                                    val pending = prefs.places()
                                        .filter(SavedPlaceMetadataPolicy::incomplete).take(5)
                                    pending.forEach { place ->
                                        val details = placeLookup.suggest(place.coordinate)
                                        if (prefs.enrichSavedPlace(place.id, place.coordinate, details))
                                            improved++
                                        attempted++
                                        places = prefs.places()
                                        kotlinx.coroutines.delay(900L) // courteous public API pacing
                                    }
                                    backfillResult = "本次查詢 $attempted 筆，補齊 $improved 筆；" +
                                        "無結果者保留原資料，可稍後再次嘗試"
                                } catch (e: Exception) {
                                    backfillResult = "補齊中斷：${e.message ?: "網路不可用"}，既有收藏不受影響"
                                } finally {
                                    backfillingPlaces = false
                                    places = prefs.places()
                                }
                            }
                        }
                    },
                    routeContent = { query ->
                        RouteLibraryPanel(
                            mode = WalkMode.ROUTE,
                            draft = roadStops,
                            routes = savedRoutes.filter {
                                query.isBlank() || it.name.contains(query, ignoreCase = true)
                            },
                            foreground = foreground, muted = muted,
                            onSave = { name -> saveRoute(name, WalkMode.ROUTE, roadStops) },
                            onRename = ::renameRoute,
                            onLoad = { route ->
                                loadRoute(route)
                                showFavourites = false
                            },
                            onExport = { route ->
                                exportRouteId = route.id
                                exportGpx.launch(route.fileName())
                            },
                            onDelete = ::deleteRoute,
                            onImport = {
                                importMode = WalkMode.ROUTE
                                importGpx.launch(arrayOf(
                                    "application/gpx+xml", "application/xml", "text/xml", "*/*"))
                            },
                        )
                        TextButton(onClick = {
                            exportFlowers.launch("PikminBee-legacy-flowers.geojson")
                        }) {
                            Text("匯出舊花點資料（GeoJSON）", fontSize = 12.sp)
                        }
                    },
                )
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
        }
    }
}
