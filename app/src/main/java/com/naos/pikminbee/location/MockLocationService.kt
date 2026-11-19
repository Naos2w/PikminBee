package com.naos.pikminbee.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.naos.pikminbee.MainActivity
import com.naos.pikminbee.ui.FloatingControls
import com.naos.pikminbee.domain.FloatingRouteDraft
import com.naos.pikminbee.location.OverlayMapSelection
import com.naos.pikminbee.health.EstimatedStepCounter
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.health.EstimatedStepsSyncWorker
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.route.ValhallaPlanner
import com.naos.pikminbee.lan.LanController
import com.naos.pikminbee.lan.LanStatus
import androidx.core.content.ContextCompat
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkingGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

/** UI, future overlay and LAN controller must all observe this single GPS state. */
object LocationStatus {
    val display = MutableStateFlow("真實定位")
    val selected = MutableStateFlow<Coordinate?>(null)
    val phase = MutableStateFlow(WalkPhase.IDLE)
    val mode = MutableStateFlow<WalkMode?>(null)
    val geometry = MutableStateFlow<List<Coordinate>>(emptyList())
    val overlayVisible = MutableStateFlow(false)
}

class MockLocationService : Service() {
    companion object {
        const val ACTION_TELEPORT = "com.naos.pikminbee.action.TELEPORT"
        const val ACTION_STOP = "com.naos.pikminbee.action.STOP_MOCK"
        const val ACTION_START_ROUTE = "com.naos.pikminbee.action.START_ROUTE"
        const val ACTION_PAUSE_ROUTE = "com.naos.pikminbee.action.PAUSE_ROUTE"
        const val ACTION_RESUME_ROUTE = "com.naos.pikminbee.action.RESUME_ROUTE"
        const val ACTION_STOP_ROUTE = "com.naos.pikminbee.action.STOP_ROUTE"
        const val ACTION_CLEAR_ROUTE = "com.naos.pikminbee.action.CLEAR_ROUTE"
        const val ACTION_START_FLOWERS = "com.naos.pikminbee.action.START_FLOWER_DRAFT"
        const val ACTION_START_DRAFT_ROUTE = "com.naos.pikminbee.action.START_ROUTE_DRAFT"
        const val ACTION_SET_SPEED = "com.naos.pikminbee.action.SET_WALK_SPEED"
        const val ACTION_SHOW_OVERLAY = "com.naos.pikminbee.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.naos.pikminbee.action.HIDE_OVERLAY"
        const val ACTION_ENABLE_LAN = "com.naos.pikminbee.action.ENABLE_LAN"
        const val ACTION_DISABLE_LAN = "com.naos.pikminbee.action.DISABLE_LAN"
        const val ACTION_APPROVE_LAN = "com.naos.pikminbee.action.APPROVE_LAN"
        const val ACTION_DENY_LAN = "com.naos.pikminbee.action.DENY_LAN"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_GEOMETRY = "route_geometry"
        const val EXTRA_SPEED = "speed_mps"
        const val EXTRA_SPEED_PRESET = "walk_speed_preset"
        const val EXTRA_MODE = "walk_mode"
        const val EXTRA_LOOP = "route_loop"
        private const val CHANNEL = "pikminbee_active"
        private const val NOTIFICATION_ID = 301

        fun teleportIntent(context: android.content.Context, point: Coordinate): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_TELEPORT)
                .putExtra(EXTRA_LAT, point.latitude).putExtra(EXTRA_LON, point.longitude)

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)

        fun command(context: android.content.Context, action: String): Intent =
            Intent(context, MockLocationService::class.java).setAction(action)

        fun speedIntent(context: android.content.Context, preset: WalkSpeedPreset): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_SET_SPEED)
                .putExtra(EXTRA_SPEED_PRESET, preset.name)

        fun routeIntent(context: android.content.Context, track: List<Coordinate>, speed: Double,
                        mode: WalkMode = WalkMode.ROUTE, loop: Boolean = false): Intent {
            require(track.size in 2..10_000) { "Invalid walking route" }
            val packed = DoubleArray(track.size * 2) { index ->
                val point = track[index / 2]
                if (index % 2 == 0) point.latitude else point.longitude
            }
            return Intent(context, MockLocationService::class.java).setAction(ACTION_START_ROUTE)
                .putExtra(EXTRA_GEOMETRY, packed)
                .putExtra(EXTRA_SPEED, speed.coerceIn(0.2, 3.0))
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LOOP, loop)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: LocationManager
    private var providerActive = false
    private var target: Coordinate? = null
    private var track = emptyList<Coordinate>()
    private var trackLengthMeters = 0.0
    private var walkingDistanceMeters = 0.0
    private val estimatedSteps = EstimatedStepCounter()
    private var estimatedStepPersistTicker = 0
    private var sessionStartMillis = 0L
    private var sessionStartSteps = 0L
    private var speedMetersPerSecond = WalkSpeedPreset.DEFAULT.metresPerSecond
    private var loopRoute = false
    private var previousTickMillis = 0L
    private var phase = WalkPhase.IDLE
    private var mode: WalkMode? = null
    private var floatingControls: FloatingControls? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var flowerPlanJob: Job? = null
    private var flowerPlanRevision = 0
    private var lanController: LanController? = null
    private lateinit var journal: SessionJournal

    private val tick = object : Runnable {
        override fun run() {
            if (!providerActive) return
            val now = SystemClock.elapsedRealtime()
            val deltaSeconds = ((now - previousTickMillis) / 1000.0).coerceIn(0.0, 2.0)
            previousTickMillis = now
            if (phase == WalkPhase.MOVING && track.size > 1) {
                val travelMetres = speedMetersPerSecond * deltaSeconds
                val acceptedMetres = if (loopRoute) travelMetres else
                    travelMetres.coerceAtMost((trackLengthMeters - walkingDistanceMeters).coerceAtLeast(0.0))
                if (acceptedMetres > 0) {
                    EstimatedWalkStatus.state.value = estimatedSteps.addTravel(acceptedMetres)
                    if (++estimatedStepPersistTicker >= 15) {
                        saveEstimatedSteps()
                        estimatedStepPersistTicker = 0
                    }
                }
                walkingDistanceMeters += travelMetres
                if (loopRoute && trackLengthMeters > 0.1 && walkingDistanceMeters >= trackLengthMeters) {
                    walkingDistanceMeters %= trackLengthMeters
                }
                target = WalkingGeometry.sampleAt(track, walkingDistanceMeters)
                if (!loopRoute && walkingDistanceMeters >= trackLengthMeters - 0.1) {
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "已抵達終點 · 原地停留"
                    finishEstimatedSession()
                    refreshNotification()
                }
            }
            target?.let { point ->
                pushFix(point)
                if (providerActive) {
                    journal.record(point)
                    LocationStatus.selected.value = point
                }
            }
            updateOverlay()
            lanController?.checkNetwork()
            if (providerActive) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        // Also initialise saved route drafts when Android restores the service
        // before the app Activity has ever been opened in this process.
        AppPreferences(this)
        val previousEstimated = getSharedPreferences("pikminbee_estimated_steps", MODE_PRIVATE)
            .getFloat("total_metres", 0f).toDouble()
        estimatedSteps.restore(previousEstimated.coerceAtLeast(0.0))
        EstimatedWalkStatus.state.value = estimatedSteps.snapshot()
        speedMetersPerSecond = WalkSpeedState.selected.value.metresPerSecond
        journal = SessionJournal(this)
        journal.read()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "PikminBee 模擬位置", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                // START_STICKY may redeliver a null Intent after the OS reclaims our process.
                // Only restore the last virtual position as PARKED. Never replay a route
                // or reopen overlay/LAN control without the user's explicit interaction.
                val previous = journal.read()
                if (previous == null || !ensureProvider()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                track = emptyList()
                trackLengthMeters = 0.0
                loopRoute = false
                mode = null
                phase = WalkPhase.PARKED
                target = previous.coordinate
                LocationStatus.phase.value = phase
                LocationStatus.mode.value = null
                LocationStatus.geometry.value = emptyList()
                LocationStatus.display.value = "系統已恢復定位 · 原地暫停"
                scheduleTicks()
                refreshNotification()
                return START_STICKY
            }
            ACTION_ENABLE_LAN -> {
                if (!providerActive) {
                    LanStatus.display.value = LanStatus.display.value.copy(
                        active = false, note = "請先在手機啟用測試定位，才能開啟 Wi-Fi 控制")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val current = lanController ?: LanController(this).also { lanController = it }
                current.start()
                return START_STICKY
            }
            ACTION_DISABLE_LAN -> {
                lanController?.close()
                lanController = null
                return START_STICKY
            }
            ACTION_APPROVE_LAN -> {
                lanController?.approvePending()
                return START_STICKY
            }
            ACTION_DENY_LAN -> {
                lanController?.denyPending()
                return START_STICKY
            }
            ACTION_SHOW_OVERLAY -> {
                // Showing controls is independent of whether mock GPS is running.
                // Actual location-changing actions still check permissions via ensureProvider().
                if (!Settings.canDrawOverlays(this)) {
                    LocationStatus.display.value = "請先授予 PikminBee 懸浮視窗權限"
                    return if (providerActive) START_STICKY else START_NOT_STICKY
                }
                runCatching {
                    if (floatingControls == null) {
                        floatingControls = FloatingControls(this,
                            sendAction = { action ->
                                val actionName = when (action) {
                                    FloatingControls.PAUSE -> ACTION_PAUSE_ROUTE
                                    FloatingControls.RESUME -> ACTION_RESUME_ROUTE
                                    FloatingControls.STOP_WALK -> ACTION_STOP_ROUTE
                                    FloatingControls.START_FLOWERS -> ACTION_START_FLOWERS
                                    FloatingControls.RESTORE_REAL -> ACTION_STOP
                                    else -> ACTION_HIDE_OVERLAY
                                }
                                startService(command(this, actionName))
                            },
                            onTeleport = { point -> startService(teleportIntent(this, point)) },
                            onStartRoute = { startService(command(this, ACTION_START_DRAFT_ROUTE)) },
                            onOpenSettings = {
                                startActivity(Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            },
                            onSpeedSelected = { preset ->
                                startService(speedIntent(this, preset))
                            },
                            openMap = {
                                startActivity(Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            })
                    }
                    floatingControls?.show()
                    updateOverlay()
                    LocationStatus.overlayVisible.value = true
                    LocationStatus.display.value = if (providerActive)
                        "懸浮蜜蜂已開啟 · 可操作路徑" else "懸浮蜜蜂已開啟 · 定位測試尚未啟動"
                    updateOverlay()
                }.onFailure {
                    LocationStatus.overlayVisible.value = false
                    LocationStatus.display.value = "無法顯示懸浮視窗：" + (it.message ?: "權限問題")
                }
                // An overlay-only service is not a location foreground service:
                // do not restart it automatically if Android reclaims the process.
                return if (providerActive) START_STICKY else START_NOT_STICKY
            }
            ACTION_SET_SPEED -> {
                val requested = intent.getStringExtra(EXTRA_SPEED_PRESET)
                val preset = WalkSpeedPreset.entries.firstOrNull { it.name == requested }
                    ?: return START_NOT_STICKY
                // The user may preselect walking speed from the floating bee
                // even before a mock location session has begun.
                AppPreferences(this).setWalkSpeedPreset(preset)
                speedMetersPerSecond = preset.metresPerSecond
                if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
                    val activity = if (mode == WalkMode.FLOWERS) "尋花" else "道路"
                    LocationStatus.display.value =
                        "$activity${if (phase == WalkPhase.PAUSED) "暫停" else "步行"} · ${preset.display}"
                    refreshNotification()
                }
                if (!providerActive) {
                    LocationStatus.display.value = "已選擇 ${preset.display} · 下次行走時使用"
                    updateOverlay()
                }
                return if (providerActive) START_STICKY else START_NOT_STICKY
            }
            ACTION_START_FLOWERS, ACTION_START_DRAFT_ROUTE -> {
                return startFloatingDraft(
                    if (intent.action == ACTION_START_FLOWERS) WalkMode.FLOWERS else WalkMode.ROUTE
                )
            }
            ACTION_HIDE_OVERLAY -> {
                floatingControls?.dismiss()
                floatingControls = null
                LocationStatus.overlayVisible.value = false
                LocationStatus.display.value = if (providerActive) "懸浮控制已關閉 · 模擬定位保持" else "懸浮控制已關閉"
                if (!providerActive) stopSelf()
                return if (providerActive) START_STICKY else START_NOT_STICKY
            }
            ACTION_STOP -> {
                releaseMock()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_ROUTE -> {
                if (phase == WalkPhase.MOVING) {
                    phase = WalkPhase.PAUSED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "步行已暫停"
                    target?.let { journal.record(it, force = true) }
                    refreshNotification()
                }
                return START_STICKY
            }
            ACTION_RESUME_ROUTE -> {
                if (phase == WalkPhase.PAUSED) {
                    previousTickMillis = SystemClock.elapsedRealtime()
                    phase = WalkPhase.MOVING
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "沿道路步行中"
                    refreshNotification()
                }
                return START_STICKY
            }
            ACTION_STOP_ROUTE -> {
                if (flowerPlanJob?.isActive == true) {
                    cancelFlowerPlanning()
                    LocationStatus.display.value = "尋花道路規劃已取消 · 目前位置不變"
                    updateOverlay()
                    refreshNotification()
                    return START_STICKY
                }
                if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "步行已停止 · 保留目前位置"
                    target?.let { journal.record(it, force = true) }
                    finishEstimatedSession()
                    saveEstimatedSteps()
                    refreshNotification()
                }
                return START_STICKY
            }
            ACTION_CLEAR_ROUTE -> {
                finishEstimatedSession()
                cancelFlowerPlanning()
                track = emptyList()
                trackLengthMeters = 0.0
                walkingDistanceMeters = 0.0
                loopRoute = false
                mode = null
                phase = WalkPhase.IDLE
                LocationStatus.phase.value = phase
                LocationStatus.mode.value = null
                LocationStatus.geometry.value = emptyList()
                LocationStatus.display.value =
                    if (providerActive) "模擬位置停留中" else "真實定位"
                if (providerActive) target?.let { journal.record(it, force = true) }
                refreshNotification()
                return START_STICKY
            }
            ACTION_TELEPORT -> {
                finishEstimatedSession()
                val point = runCatching {
                    Coordinate(intent.getDoubleExtra(EXTRA_LAT, Double.NaN),
                        intent.getDoubleExtra(EXTRA_LON, Double.NaN))
                }.getOrNull() ?: return START_NOT_STICKY
                cancelFlowerPlanning()
                if (!ensureProvider()) return START_NOT_STICKY
                phase = WalkPhase.IDLE
                mode = null
                track = emptyList()
                trackLengthMeters = 0.0
                LocationStatus.phase.value = phase
                LocationStatus.mode.value = null
                LocationStatus.geometry.value = emptyList()
                target = point
                journal.record(point, force = true)
                LocationStatus.display.value = "瞬移後原地停留"
                scheduleTicks()
                refreshNotification()
                return START_STICKY
            }
            ACTION_START_ROUTE -> {
                val raw = intent.getDoubleArrayExtra(EXTRA_GEOMETRY) ?: return START_NOT_STICKY
                if (raw.size !in 4..20_000 || raw.size % 2 != 0) return START_NOT_STICKY
                val planned = runCatching {
                    raw.toList().chunked(2).map { Coordinate(it[0], it[1]) }
                }.getOrNull() ?: return START_NOT_STICKY
                val routeMode = runCatching {
                    WalkMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "")
                }.getOrDefault(WalkMode.ROUTE)
                cancelFlowerPlanning()
                return if (activateRoute(
                        planned,
                        intent.getDoubleExtra(
                            EXTRA_SPEED, WalkSpeedState.selected.value.metresPerSecond,
                        ),
                        routeMode,
                        intent.getBooleanExtra(EXTRA_LOOP, false),
                    )
                ) START_STICKY else START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }


    private fun finishEstimatedSession() {
        val start = sessionStartMillis
        if (start <= 0L) return
        sessionStartMillis = 0L
        val estimated = (estimatedSteps.snapshot().estimatedSteps - sessionStartSteps)
            .coerceAtLeast(0L)
        if (estimated == 0L) return
        val ledger = EstimatedStepLedger(this)
        val stored = runCatching {
            ledger.add(start, System.currentTimeMillis(), estimated)
        }.getOrElse { error ->
            LocationStatus.display.value = "估算步數保存失敗：" + error.message
            return
        }
        if (stored != null && ledger.optIn()) {
            runCatching { EstimatedStepsSyncWorker.enqueue(this) }
                .onFailure { LocationStatus.display.value = "Health Connect 同步排程失敗：" + it.message }
        }
    }

    private fun saveEstimatedSteps() {
        getSharedPreferences("pikminbee_estimated_steps", MODE_PRIVATE).edit()
            .putFloat("total_metres", estimatedSteps.snapshot().totalMetres.toFloat())
            .apply()
    }

    /** Current virtual fix or a recently observed device fix; never the first route waypoint. */
    private fun floatingOrigin(): Coordinate? {
        target?.let { return it }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        if (System.currentTimeMillis() - OverlayMapSelection.deviceFixTimestampMillis.value
            < 300_000L) {
            OverlayMapSelection.deviceFix.value?.let { return it }
        }
        return sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                    ?.takeIf { it.time > System.currentTimeMillis() - 300_000L }
            }
            .firstOrNull()
            ?.let { fix -> runCatching { Coordinate(fix.latitude, fix.longitude) }.getOrNull() }
    }

    private fun startFloatingDraft(walkMode: WalkMode): Int {
        val label = if (walkMode == WalkMode.FLOWERS) "尋花" else "路徑"
        if (flowerPlanJob?.isActive == true) {
            LocationStatus.display.value = "已有道路規劃正在進行，請先取消"
            updateOverlay()
            return if (providerActive) START_STICKY else START_NOT_STICKY
        }
        if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
            LocationStatus.display.value = "請先停止目前行走，再啟動新的" + label
            updateOverlay()
            return START_STICKY
        }
        val current = floatingOrigin()
        val stops = if (walkMode == WalkMode.FLOWERS) RouteDraftState.flowerStops.value
            else RouteDraftState.routeStops.value
        val anchors = runCatching {
            FloatingRouteDraft.anchors(current, stops, loop = walkMode == WalkMode.FLOWERS)
        }.getOrElse { error ->
            LocationStatus.display.value = label + "無法開始：" +
                (error.message ?: "請檢查路點")
            updateOverlay()
            return if (providerActive) START_STICKY else START_NOT_STICKY
        }
        val revision = ++flowerPlanRevision
        LocationStatus.display.value = label + "道路規劃中 · 尚未移動"
        updateOverlay()
        flowerPlanJob = serviceScope.launch {
            try {
                val planned = ValhallaPlanner.plan(anchors)
                if (revision != flowerPlanRevision ||
                    phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED ||
                    (providerActive && target != current)) return@launch
                activateRoute(
                    planned,
                    WalkSpeedState.selected.value.metresPerSecond,
                    walkMode,
                    loop = walkMode == WalkMode.FLOWERS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (revision == flowerPlanRevision) {
                    LocationStatus.display.value = label + "道路規劃失敗：" +
                        (error.message ?: "網路或路點錯誤")
                }
            } finally {
                if (revision == flowerPlanRevision) {
                    flowerPlanJob = null
                    updateOverlay()
                    refreshNotification()
                }
            }
        }
        return if (providerActive) START_STICKY else START_NOT_STICKY
    }

    private fun updateOverlay() {
        floatingControls?.update(
            phase = phase,
            mode = mode,
            flowerStops = RouteDraftState.flowerStops.value.size,
            speedPreset = WalkSpeedState.selected.value,
            defaultSpeedPreset = WalkSpeedState.preferred.value,
            planningFlowers = flowerPlanJob?.isActive == true,
            status = LocationStatus.display.value,
        )
    }

    private fun cancelFlowerPlanning() {
        flowerPlanRevision++
        flowerPlanJob?.cancel()
        flowerPlanJob = null
    }

    /** Shared path for route intents and the explicit overlay flower shortcut. */
    private fun activateRoute(
        planned: List<Coordinate>,
        speed: Double,
        routeMode: WalkMode,
        loop: Boolean,
    ): Boolean {
        val length = WalkingGeometry.lengthMeters(planned)
        if (planned.size !in 2..10_000 || length !in 0.1..80_000.0) {
            LocationStatus.display.value = "步行路線無效"
            updateOverlay()
            return false
        }
        if (!ensureProvider()) return false
        finishEstimatedSession()
        sessionStartMillis = System.currentTimeMillis()
        sessionStartSteps = estimatedSteps.snapshot().estimatedSteps
        track = planned
        trackLengthMeters = length
        target = planned.first()
        journal.record(planned.first(), force = true)
        speedMetersPerSecond = speed.coerceIn(0.2, 3.0)
        walkingDistanceMeters = 0.0
        loopRoute = loop
        mode = routeMode
        phase = WalkPhase.MOVING
        LocationStatus.phase.value = phase
        LocationStatus.mode.value = mode
        LocationStatus.geometry.value = track
        LocationStatus.display.value = if (mode == WalkMode.FLOWERS)
            "尋花步行中" else "沿道路步行中"
        scheduleTicks()
        refreshNotification()
        return true
    }

    private fun ensureProvider(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            LocationStatus.display.value = "請先授予精確定位權限"
            return false
        }
        return try {
            startForeground(NOTIFICATION_ID, notification())
            if (!providerActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val props = android.location.provider.ProviderProperties.Builder()
                        .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
                        .setHasAltitudeSupport(true).setHasSpeedSupport(true)
                        .setHasBearingSupport(true).build()
                    manager.addTestProvider(LocationManager.GPS_PROVIDER, props)
                } else {
                    registerLegacyProvider()
                }
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                providerActive = true
            }
            true
        } catch (e: Exception) {
            LocationStatus.display.value = "請在 Android 開發者選項中選取 PikminBee 作為模擬位置 App"
            releaseMock(clearCheckpoint = false)
            stopSelf()
            false
        }
    }

    private fun scheduleTicks() {
        handler.removeCallbacks(tick)
        previousTickMillis = SystemClock.elapsedRealtime()
        tick.run()
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("WrongConstant")
    private fun registerLegacyProvider() {
        manager.addTestProvider(LocationManager.GPS_PROVIDER,
            false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
    }

    private fun pushFix(point: Coordinate) {
        try {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                altitude = 0.0
                speed = if (phase == WalkPhase.MOVING) speedMetersPerSecond.toFloat() else 0f
            })
        } catch (e: Exception) {
            LocationStatus.display.value = "模擬定位中斷，已停止"
            releaseMock(clearCheckpoint = false)
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PikminBee · 測試定位")
            .setContentText(LocationStatus.display.value)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
            val paused = phase == WalkPhase.PAUSED
            val action = if (paused) ACTION_RESUME_ROUTE else ACTION_PAUSE_ROUTE
            val toggle = PendingIntent.getService(
                this, if (paused) 2 else 1, command(this, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    this, if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                ),
                if (paused) "繼續" else "暫停", toggle,
            ).build())
            val stopWalk = PendingIntent.getService(
                this, 3, command(this, ACTION_STOP_ROUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    this, android.R.drawable.ic_menu_close_clear_cancel,
                ),
                "停止行走", stopWalk,
            ).build())
        }

        val restoreReal = PendingIntent.getService(
            this, 0, stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_mylocation),
            "恢復真實 GPS", restoreReal,
        ).build())
        return builder.build()
    }

    private fun refreshNotification() {
        if (providerActive) {
            runCatching {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification())
            }
        }
    }

    private fun releaseMock(clearCheckpoint: Boolean = true) {
        finishEstimatedSession()
        saveEstimatedSteps()
        cancelFlowerPlanning()
        if (clearCheckpoint) journal.clear()
        lanController?.close()
        lanController = null
        floatingControls?.dismiss()
        floatingControls = null
        LocationStatus.overlayVisible.value = false
        providerActive = false
        handler.removeCallbacks(tick)
        target = null
        track = emptyList()
        trackLengthMeters = 0.0
        mode = null
        loopRoute = false
        phase = WalkPhase.IDLE
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        LocationStatus.selected.value = null
        LocationStatus.geometry.value = emptyList()
        LocationStatus.phase.value = WalkPhase.IDLE
        LocationStatus.mode.value = null
        LocationStatus.display.value = "真實定位"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Refit the existing overlay after portrait/landscape changes.
        // This never restarts GPS walking or creates a new overlay.
        floatingControls?.onScreenChanged()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The foreground service normally survives removing the UI from Recents.
        // Save the latest position but never start an alarm/service workaround.
        if (providerActive) target?.let { journal.record(it, force = true) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // This can be a system shutdown of the service; only explicit ACTION_STOP
        // is allowed to delete a useful checkpoint.
        releaseMock(clearCheckpoint = false)
        serviceScope.cancel()
        super.onDestroy()
    }
}
