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
import android.util.Log
import com.naos.pikminbee.MainActivity
import com.naos.pikminbee.ui.FloatingControls
import com.naos.pikminbee.domain.FloatingRouteDraft
import com.naos.pikminbee.location.OverlayMapSelection
import com.naos.pikminbee.health.EstimatedStepCounter
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.health.EstimatedStepsSyncWorker
import com.naos.pikminbee.health.VirtualEstimateSyncPolicy
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.PreparedRouteState
import com.naos.pikminbee.data.RouteDraftState
import com.naos.pikminbee.data.WalkSpeedState
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.domain.WalkSpeedPlayback
import com.naos.pikminbee.domain.RouteTickProgression
import com.naos.pikminbee.route.ValhallaPlanner
import com.naos.pikminbee.lan.LanController
import com.naos.pikminbee.lan.LanStatus
import androidx.core.content.ContextCompat
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.domain.MockGpsJitter
import com.naos.pikminbee.domain.RoutePlaybackProgress
import com.naos.pikminbee.domain.RouteCompletionAlertPolicy
import com.naos.pikminbee.domain.RouteLifecycleChange
import com.naos.pikminbee.domain.RouteSurfaceStatusPolicy
import com.naos.pikminbee.domain.WalkingProgressSnapshot
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
    /** True only while the Android test GPS provider is installed; never label its fix real. */
    val mockActive = MutableStateFlow(false)
    val geometry = MutableStateFlow<List<Coordinate>>(emptyList())
    val overlayVisible = MutableStateFlow(false)
    val progress = MutableStateFlow(WalkingProgressSnapshot())
    /** Steps estimated for the current/most recently stopped virtual walk only. */
    val walkEstimatedSteps = MutableStateFlow(0L)
    /** Actual virtual route speed, not a sensor-measured walking pace. */
    val appliedSpeedKmh = MutableStateFlow(0.0)
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
        const val ACTION_START_DRAFT_ROUTE = "com.naos.pikminbee.action.START_ROUTE_DRAFT"
        const val ACTION_SET_SPEED = "com.naos.pikminbee.action.SET_WALK_SPEED"
        const val ACTION_SHOW_OVERLAY = "com.naos.pikminbee.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.naos.pikminbee.action.HIDE_OVERLAY"
        const val ACTION_REAL_GPS_OVERLAY = "com.naos.pikminbee.action.REAL_GPS_OVERLAY"
        const val ACTION_VIRTUAL_GPS_OVERLAY = "com.naos.pikminbee.action.VIRTUAL_GPS_OVERLAY"
        const val ACTION_REFRESH_OVERLAY_APPEARANCE = "com.naos.pikminbee.action.REFRESH_OVERLAY_APPEARANCE"
        const val ACTION_ENABLE_LAN = "com.naos.pikminbee.action.ENABLE_LAN"
        const val ACTION_DISABLE_LAN = "com.naos.pikminbee.action.DISABLE_LAN"
        const val ACTION_APPROVE_LAN = "com.naos.pikminbee.action.APPROVE_LAN"
        const val ACTION_DENY_LAN = "com.naos.pikminbee.action.DENY_LAN"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_GEOMETRY = "route_geometry"
        const val EXTRA_SPEED = "speed_mps"
        const val EXTRA_SPEED_PRESET = "walk_speed_preset"
        const val EXTRA_SPEED_MODE = "walk_speed_mode"
        const val EXTRA_SPEED_KMH = "walk_speed_kmh"
        const val EXTRA_SPEED_MIN_KMH = "walk_speed_min_kmh"
        const val EXTRA_SPEED_MAX_KMH = "walk_speed_max_kmh"
        const val EXTRA_MODE = "walk_mode"
        const val EXTRA_LOOP = "route_loop"
        private const val CHANNEL = "pikminbee_active"
        private const val ARRIVAL_CHANNEL = "pikminbee_route_arrival"
        private const val NOTIFICATION_ID = 301
        private const val ARRIVAL_NOTIFICATION_ID = 302

        fun teleportIntent(context: android.content.Context, point: Coordinate): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_TELEPORT)
                .putExtra(EXTRA_LAT, point.latitude).putExtra(EXTRA_LON, point.longitude)

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)

        fun command(context: android.content.Context, action: String): Intent =
            Intent(context, MockLocationService::class.java).setAction(action)

        fun speedIntent(context: android.content.Context, preset: WalkSpeedPreset): Intent =
            speedIntent(context, WalkSpeedProfile.preset(preset))

        fun speedIntent(context: android.content.Context, profile: WalkSpeedProfile): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_SET_SPEED)
                .putExtra(EXTRA_SPEED_PRESET, profile.preset.name)
                .putExtra(EXTRA_SPEED_MODE, profile.mode.name)
                .putExtra(EXTRA_SPEED_KMH, profile.fixedKmh)
                .putExtra(EXTRA_SPEED_MIN_KMH, profile.minKmh)
                .putExtra(EXTRA_SPEED_MAX_KMH, profile.maxKmh)

        fun routeIntent(context: android.content.Context, track: List<Coordinate>, speed: Double,
                        mode: WalkMode = WalkMode.ROUTE, loop: Boolean = false): Intent {
            require(track.size in 2..10_000) { "Invalid walking route" }
            val packed = DoubleArray(track.size * 2) { index ->
                val point = track[index / 2]
                if (index % 2 == 0) point.latitude else point.longitude
            }
            return Intent(context, MockLocationService::class.java).setAction(ACTION_START_ROUTE)
                .putExtra(EXTRA_GEOMETRY, packed)
                .putExtra(EXTRA_SPEED, (speed.takeIf { it.isFinite() }
                    ?: WalkSpeedPreset.DEFAULT.metresPerSecond).coerceIn(
                        WalkSpeedProfile.MIN_KMH / 3.6, WalkSpeedProfile.MAX_KMH / 3.6))
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LOOP, loop)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: LocationManager
    private lateinit var appPreferences: AppPreferences
    private var providerActive = false
    private var target: Coordinate? = null
    private var track = emptyList<Coordinate>()
    private var trackLengthMeters = 0.0
    private var walkingDistanceMeters = 0.0
    private var stopThresholds = emptyList<Double>()
    private val estimatedSteps = EstimatedStepCounter()
    private val speedPlayback = WalkSpeedPlayback(WalkSpeedProfile.preset(WalkSpeedPreset.DEFAULT))
    private val mockGpsJitter = MockGpsJitter()
    private var estimatedStepPersistTicker = 0
    private var sessionStartMillis = 0L
    private var sessionStartSteps = 0L
    private var walkOriginSteps = 0L
    private var speedMetersPerSecond = WalkSpeedPreset.DEFAULT.metresPerSecond
    private var loopRoute = false
    private var routeArrivalNotified = false
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
            var advancedMetres = 0.0
            if (phase == WalkPhase.MOVING && track.size > 1) {
                val movement = RouteTickProgression.advance(
                    walkingDistanceMeters, trackLengthMeters,
                    speedMetersPerSecond, deltaSeconds, loopRoute,
                )
                if (movement.acceptedMetres > 0.0) {
                    advancedMetres = movement.acceptedMetres
                    val snapshot = estimatedSteps.addTravel(movement.acceptedMetres)
                    EstimatedWalkStatus.state.value = snapshot
                    LocationStatus.walkEstimatedSteps.value =
                        estimatedSteps.estimatedSince(walkOriginSteps)
                    if (++estimatedStepPersistTicker >= 15) {
                        saveEstimatedSteps()
                        refreshNotification()
                        estimatedStepPersistTicker = 0
                    }
                    // Health Connect does not receive live GPS ticks. Persist a
                    // bounded, explicitly opted-in estimate chunk per minute.
                    checkpointEstimatedSession()
                }
                // Loop progress wraps, but ALL completed laps still accrue exact
                // route distance and trigger any crossed speed segment boundary.
                walkingDistanceMeters = movement.toMetres
                target = WalkingGeometry.sampleAt(track, walkingDistanceMeters)
                speedPlayback.advance(
                    movement.fromMetres, movement.acceptedMetres,
                    trackLengthMeters, stopThresholds, loopRoute,
                )
                val updatedKmh = speedPlayback.appliedKmh
                if (updatedKmh != LocationStatus.appliedSpeedKmh.value) {
                    speedMetersPerSecond = updatedKmh / 3.6
                    LocationStatus.appliedSpeedKmh.value = updatedKmh
                }
                if (movement.arrived) {
                    val previousPhase = phase
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "已抵達終點 · 原地停留"
                    LocationStatus.appliedSpeedKmh.value =
                        RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
                            phase, speedMetersPerSecond * 3.6)
                    finishEstimatedSession()
                    refreshNotification()
                    if (RouteCompletionAlertPolicy.shouldAlert(
                            previousPhase, phase, loopRoute, routeArrivalNotified)) {
                        routeArrivalNotified = true
                        showRouteArrivalNotification()
                    }
                }
            }
            target?.let { point ->
                // Only the Android test provider receives the small reported drift.
                // Journal, app/Web state, route progress and step count keep the
                // original, exact planned location so no phantom distance accrues.
                val reported = mockGpsJitter.report(
                    planned = point,
                    level = appPreferences.mockGpsJitterLevel(),
                    routeActive = mode != null && track.size > 1,
                    advanced = advancedMetres > 0.0,
                )
                pushFix(reported)
                if (providerActive) {
                    journal.record(point)
                    LocationStatus.selected.value = point
                }
            }
            if (track.size >= 2) {
                LocationStatus.progress.value = RoutePlaybackProgress.snapshot(
                    walkingDistanceMeters, trackLengthMeters,
                    LocationStatus.appliedSpeedKmh.value / 3.6,
                    stopThresholds, loopRoute,
                )
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
        appPreferences = AppPreferences(this)
        val savedSteps = getSharedPreferences("pikminbee_estimated_steps", MODE_PRIVATE)
        val previousEstimated = savedSteps.getFloat("total_metres", 0f).toDouble()
        estimatedSteps.restore(
            previousEstimated.coerceAtLeast(0.0),
            savedSteps.getLong("estimated_steps_total", 0L)
                .takeIf { savedSteps.contains("estimated_steps_total") },
            savedSteps.getFloat("step_remainder_metres", 0f).toDouble()
                .takeIf { savedSteps.contains("step_remainder_metres") },
            savedSteps.getFloat("step_stride_metres", 0.72f).toDouble()
                .takeIf { savedSteps.contains("step_stride_metres") },
        )
        EstimatedWalkStatus.state.value = estimatedSteps.snapshot()
        speedMetersPerSecond = WalkSpeedState.profile.value.nominalMps
        journal = SessionJournal(this)
        journal.read()
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(
                CHANNEL, "PikminBee 模擬位置", NotificationManager.IMPORTANCE_LOW))
            // Android notification settings provide per-channel sound/vibration control.
            createNotificationChannel(NotificationChannel(
                ARRIVAL_CHANNEL, "PikminBee 路線完成提醒", NotificationManager.IMPORTANCE_DEFAULT))
        }
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
                stopThresholds = emptyList()
                LocationStatus.progress.value = WalkingProgressSnapshot()
                LocationStatus.walkEstimatedSteps.value = 0L
                LocationStatus.appliedSpeedKmh.value = 0.0
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
            ACTION_REFRESH_OVERLAY_APPEARANCE -> {
                floatingControls?.refreshAppearance()
                updateOverlay()
                return if (providerActive) START_STICKY else START_NOT_STICKY
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
                                    FloatingControls.RESTORE_REAL -> ACTION_REAL_GPS_OVERLAY
                                    FloatingControls.RESTORE_VIRTUAL -> ACTION_VIRTUAL_GPS_OVERLAY
                                    else -> ACTION_HIDE_OVERLAY
                                }
                                val request = command(this, actionName)
                                runCatching { startService(request) }.onFailure { failure ->
                                    // Android can reject startService from a visible
                                    // overlay after restoring REAL (no foreground mock
                                    // service). The already-running owner Service can
                                    // handle this explicit GPS click directly instead.
                                    if (actionName == ACTION_REAL_GPS_OVERLAY ||
                                        actionName == ACTION_VIRTUAL_GPS_OVERLAY) {
                                        onStartCommand(request, 0, 0)
                                    } else {
                                        LocationStatus.display.value =
                                            "無法操作懸浮控制：" + (failure.message ?: "系統限制")
                                        updateOverlay()
                                    }
                                }
                            },
                            onStartRoute = { startService(command(this, ACTION_START_DRAFT_ROUTE)) },
                            chooseVirtualPoint = {
                                // Missing saved virtual fix is not a licence to use a
                                // fabricated coordinate. Open Teleport to pick explicitly.
                                startActivity(Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_MODE, 0)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            },
                            onSpeedSelected = { preset ->
                                startService(speedIntent(this, preset))
                            },
                            openMap = {
                                startActivity(Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            },
                            )
                    }
                    floatingControls?.show()
                    updateOverlay()
                    LocationStatus.overlayVisible.value = true
                    // Overlay visibility must never overwrite the shared arrival,
                    // walking or paused route status read by the App and Wi-Fi Web.
                    LocationStatus.display.value = RouteSurfaceStatusPolicy.afterOverlayChange(
                        LocationStatus.display.value, phase, mode, providerActive, visible = true,
                    )
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
                val profile = runCatching {
                    when (intent.getStringExtra(EXTRA_SPEED_MODE) ?: "PRESET") {
                        "PRESET" -> WalkSpeedProfile.preset(preset)
                        "FIXED" -> WalkSpeedProfile.fixed(
                            intent.getDoubleExtra(EXTRA_SPEED_KMH, Double.NaN), preset)
                        "RANGE" -> WalkSpeedProfile.range(
                            intent.getDoubleExtra(EXTRA_SPEED_MIN_KMH, Double.NaN),
                            intent.getDoubleExtra(EXTRA_SPEED_MAX_KMH, Double.NaN), preset)
                        else -> return START_NOT_STICKY
                    }
                }.getOrNull() ?: return START_NOT_STICKY
                // A speed change is independent of the user's preferred startup speed.
                appPreferences.setWalkSpeedProfile(profile)
                speedPlayback.apply(profile)
                if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
                    speedMetersPerSecond = speedPlayback.appliedKmh / 3.6
                    LocationStatus.appliedSpeedKmh.value = speedPlayback.appliedKmh
                    LocationStatus.progress.value = RoutePlaybackProgress.snapshot(
                        walkingDistanceMeters, trackLengthMeters, speedMetersPerSecond,
                        stopThresholds, loopRoute)
                    val activity = if (mode == WalkMode.FLOWERS) "尋花" else "道路"
                    LocationStatus.display.value =
                        "$activity${if (phase == WalkPhase.PAUSED) "暫停" else "步行"} · ${profile.display}"
                    refreshNotification()
                }
                if (!providerActive) {
                    LocationStatus.display.value = "已選擇 ${profile.display} · 下次行走時使用"
                }
                updateOverlay()
                return if (providerActive) START_STICKY else START_NOT_STICKY
            }
            ACTION_START_DRAFT_ROUTE -> {
                return startFloatingDraft(WalkMode.ROUTE)
            }
            ACTION_HIDE_OVERLAY -> {
                floatingControls?.dismiss()
                floatingControls = null
                LocationStatus.overlayVisible.value = false
                LocationStatus.display.value = RouteSurfaceStatusPolicy.afterOverlayChange(
                    LocationStatus.display.value, phase, mode, providerActive, visible = false,
                )
                if (!providerActive) stopSelf()
                return if (providerActive) START_STICKY else START_NOT_STICKY
            }
            ACTION_REAL_GPS_OVERLAY -> {
                if (!providerActive && !LocationStatus.mockActive.value) {
                    // Tapping the active REAL button must not clear LAN, drafts,
                    // the overlay or any useful virtual-position checkpoint.
                    updateOverlay()
                    return START_NOT_STICKY
                }
                target?.let { AppPreferences(this).rememberVirtualPosition(it) }
                releaseMock(keepOverlay = true)
                updateOverlay()
                return START_NOT_STICKY
            }
            ACTION_VIRTUAL_GPS_OVERLAY -> {
                if (providerActive && LocationStatus.mockActive.value) {
                    // Already using mock GPS: do not reset or silently stop a route.
                    updateOverlay()
                    return START_STICKY
                }
                val remembered = AppPreferences(this).lastVirtualPosition()
                if (remembered == null) {
                    LocationStatus.display.value = "找不到最近的虛擬位置，請先在瞬移頁選點"
                    updateOverlay()
                    // Handle expired position between the overlay click and this
                    // command, not just the initial first-use case.
                    runCatching {
                        startActivity(Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OPEN_MODE, 0)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                    return START_NOT_STICKY
                }
                if (!ensureProvider()) {
                    updateOverlay()
                    return START_NOT_STICKY
                }
                clearOldArrivalNotification(RouteLifecycleChange.RESTORE_VIRTUAL_GPS)
                finishEstimatedSession()
                cancelFlowerPlanning()
                phase = WalkPhase.PARKED
                mode = null
                track = emptyList()
                trackLengthMeters = 0.0
                stopThresholds = emptyList()
                walkingDistanceMeters = 0.0
                LocationStatus.progress.value = WalkingProgressSnapshot()
                LocationStatus.walkEstimatedSteps.value = 0L
                LocationStatus.appliedSpeedKmh.value = 0.0
                target = remembered
                journal.record(remembered, force = true)
                LocationStatus.phase.value = phase
                LocationStatus.mode.value = null
                LocationStatus.geometry.value = emptyList()
                LocationStatus.display.value = "已回到虛擬位置 · 沒有自動行走"
                scheduleTicks()
                refreshNotification()
                // Repaint the two GPS buttons immediately after provider confirmation.
                updateOverlay()
                return START_STICKY
            }
            ACTION_STOP -> {
                target?.let { AppPreferences(this).rememberVirtualPosition(it) }
                releaseMock()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_ROUTE -> {
                if (phase == WalkPhase.MOVING) {
                    // Flush this moving interval before entering a paused state.
                    checkpointEstimatedSession(force = true)
                    phase = WalkPhase.PAUSED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "步行已暫停"
                    target?.let { journal.record(it, force = true) }
                    refreshNotification()
                    updateOverlay()
                }
                return START_STICKY
            }
            ACTION_RESUME_ROUTE -> {
                if (phase == WalkPhase.PAUSED) {
                    previousTickMillis = SystemClock.elapsedRealtime()
                    // Exclude the paused interval from the next HC estimate
                    // record after a successful pause checkpoint.
                    val currentSteps = estimatedSteps.snapshot().estimatedSteps
                    if (sessionStartSteps == currentSteps && sessionStartMillis > 0L) {
                        sessionStartMillis = System.currentTimeMillis()
                    }
                    phase = WalkPhase.MOVING
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = RouteSurfaceStatusPolicy.movingStatus(mode)
                    refreshNotification()
                    updateOverlay()
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
                    clearOldArrivalNotification(RouteLifecycleChange.MANUAL_STOP)
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "步行已停止 · 保留目前位置"
                    LocationStatus.appliedSpeedKmh.value =
                        RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
                            phase, speedMetersPerSecond * 3.6)
                    LocationStatus.progress.value = RoutePlaybackProgress.snapshot(
                        walkingDistanceMeters, trackLengthMeters, 0.0,
                        stopThresholds, loopRoute,
                    )
                    target?.let { journal.record(it, force = true) }
                    finishEstimatedSession()
                    saveEstimatedSteps()
                    refreshNotification()
                    updateOverlay()
                }
                return START_STICKY
            }
            ACTION_CLEAR_ROUTE -> {
                clearOldArrivalNotification(RouteLifecycleChange.CLEAR)
                finishEstimatedSession()
                cancelFlowerPlanning()
                track = emptyList()
                trackLengthMeters = 0.0
                stopThresholds = emptyList()
                walkingDistanceMeters = 0.0
                LocationStatus.progress.value = WalkingProgressSnapshot()
                LocationStatus.walkEstimatedSteps.value = 0L
                LocationStatus.appliedSpeedKmh.value = 0.0
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
                updateOverlay()
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
                clearOldArrivalNotification(RouteLifecycleChange.TELEPORT)
                phase = WalkPhase.IDLE
                mode = null
                track = emptyList()
                trackLengthMeters = 0.0
                stopThresholds = emptyList()
                LocationStatus.progress.value = WalkingProgressSnapshot()
                LocationStatus.walkEstimatedSteps.value = 0L
                LocationStatus.appliedSpeedKmh.value = 0.0
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
                if (routeMode != WalkMode.ROUTE) {
                    // Preserve legacy FLOWERS for saved records/GPX only: never
                    // initiate a retired mode, including through stale intents.
                    LocationStatus.display.value = "巡花模式已移除；請使用一般路徑"
                    return if (providerActive) START_STICKY else START_NOT_STICKY
                }
                cancelFlowerPlanning()
                return if (activateRoute(
                        planned,
                        intent.getDoubleExtra(
                            EXTRA_SPEED, WalkSpeedState.profile.value.nominalMps,
                        ),
                        routeMode,
                        intent.getBooleanExtra(EXTRA_LOOP, false),
                    )
                ) START_STICKY else START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }


    private fun checkpointEstimatedSession(force: Boolean = false, finish: Boolean = false) {
        val start = sessionStartMillis
        if (start <= 0L) return
        val now = System.currentTimeMillis()
        val ledger = EstimatedStepLedger(this)
        // While walking, export no more than once per minute and only if the
        // user enabled virtual-estimate sync. An unfinished walk is not a sensor
        // record and is still labelled MANUAL_ENTRY by HealthConnectStepSync.
        if (!force && !finish &&
            !VirtualEstimateSyncPolicy.shouldCheckpoint(start, now, ledger.optIn())) return
        val currentSteps = estimatedSteps.snapshot().estimatedSteps
        val deltaSteps = VirtualEstimateSyncPolicy.unsavedSteps(currentSteps, sessionStartSteps)
        if (deltaSteps == 0L) {
            if (finish) sessionStartMillis = 0L
            else if (force) sessionStartMillis = now
            return
        }
        // Do not advance the checkpoint until the interval is durably saved:
        // otherwise a full queue/disk error could permanently lose step counts.
        val stored = runCatching {
            ledger.add(start, maxOf(now, start + 1L), deltaSteps)
        }.getOrElse { error ->
            LocationStatus.display.value = "估算步數保存失敗：" + error.message
            return
        }
        if (stored != null) {
            sessionStartSteps = currentSteps
            sessionStartMillis = if (finish) 0L else now
            saveEstimatedSteps()
            if (ledger.optIn()) {
                runCatching { EstimatedStepsSyncWorker.enqueue(this) }
                    .onFailure {
                        LocationStatus.display.value =
                            "Health Connect 同步排程失敗：" + it.message
                    }
            }
        }
    }

    private fun finishEstimatedSession() {
        checkpointEstimatedSession(finish = true)
    }

    private fun saveEstimatedSteps() {
        val snapshot = estimatedSteps.snapshot()
        getSharedPreferences("pikminbee_estimated_steps", MODE_PRIVATE).edit()
            .putFloat("total_metres", snapshot.totalMetres.toFloat())
            .putLong("estimated_steps_total", snapshot.estimatedSteps)
            .putFloat("step_remainder_metres", estimatedSteps.storedRemainder().toFloat())
            .putFloat("step_stride_metres", estimatedSteps.storedStride().toFloat())
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

    /**
     * Overlay is a compact controller, not a second route planner.
     * Only the exact road preview explicitly prepared in the main app may be
     * started here. Large snap offsets require confirmation in the main app.
     */
    private fun startFloatingDraft(walkMode: WalkMode): Int {
        val label = if (walkMode == WalkMode.FLOWERS) "尋花" else "路徑"
        if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
            LocationStatus.display.value = "請先停止目前行走，再切換模式"
            updateOverlay()
            return START_STICKY
        }
        val ready = PreparedRouteState.get(walkMode)
        val origin = floatingOrigin()
        val stops = if (walkMode == WalkMode.FLOWERS)
            RouteDraftState.flowerStops.value else RouteDraftState.routeStops.value
        when {
            ready == null || !ready.isCurrent(origin, stops) ->
                LocationStatus.display.value = "請先到主畫面預覽" + label + "道路"
            ready.requiresSnapConfirmation ->
                LocationStatus.display.value = "道路吸附距離較大，請在主畫面確認後開始"
            else -> {
                val started = activateRoute(
                    ready.geometry, WalkSpeedState.profile.value.nominalMps,
                    walkMode, loop = ready.loop,
                )
                if (started) return START_STICKY
            }
        }
        updateOverlay()
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
        clearOldArrivalNotification(RouteLifecycleChange.START)
        finishEstimatedSession()
        // A newly configured stride affects only this new walk, never historical totals.
        estimatedSteps.setStrideForNextTravel(appPreferences.virtualStrideMetres())
        saveEstimatedSteps()
        sessionStartMillis = System.currentTimeMillis()
        sessionStartSteps = estimatedSteps.snapshot().estimatedSteps
        walkOriginSteps = sessionStartSteps
        LocationStatus.walkEstimatedSteps.value = 0L
        track = planned
        trackLengthMeters = length
        val draftStops = if (routeMode == WalkMode.FLOWERS)
            RouteDraftState.flowerStops.value else RouteDraftState.routeStops.value
        stopThresholds = RoutePlaybackProgress.thresholds(planned, draftStops)
        target = planned.first()
        journal.record(planned.first(), force = true)
        val profile = appPreferences.walkSpeedProfile()
        speedPlayback.restart(profile)
        speedMetersPerSecond = if (profile.mode == WalkSpeedMode.PRESET)
            (speed.takeIf { it.isFinite() } ?: profile.nominalMps).coerceIn(
                WalkSpeedProfile.MIN_KMH / 3.6, WalkSpeedProfile.MAX_KMH / 3.6)
        else speedPlayback.appliedKmh / 3.6
        LocationStatus.appliedSpeedKmh.value = speedMetersPerSecond * 3.6
        walkingDistanceMeters = 0.0
        mockGpsJitter.reset()
        loopRoute = loop
        routeArrivalNotified = false
        LocationStatus.progress.value = RoutePlaybackProgress.snapshot(
            0.0, length, speedMetersPerSecond, stopThresholds, loop,
        )
        mode = routeMode
        phase = WalkPhase.MOVING
        LocationStatus.phase.value = phase
        LocationStatus.mode.value = mode
        LocationStatus.geometry.value = track
        LocationStatus.display.value = RouteSurfaceStatusPolicy.movingStatus(mode)
        scheduleTicks()
        refreshNotification()
        updateOverlay()
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
                LocationStatus.mockActive.value = true
            }
            true
        } catch (e: Exception) {
            val appLabel = applicationInfo.loadLabel(packageManager)
            val explanation =
                "請在 Android 開發者選項中選取 $appLabel 作為模擬位置 App"
            // A failed attempt to return to virtual GPS should NOT dismiss
            // the expanded controller while the user is trying to switch.
            val preserveOverlay = floatingControls != null
            releaseMock(clearCheckpoint = false, keepOverlay = preserveOverlay,
                arrivalChange = RouteLifecycleChange.PROVIDER_FAILURE)
            // releaseMock resets display to REAL mode; preserve the failure hint.
            LocationStatus.display.value = explanation
            updateOverlay()
            if (!preserveOverlay) stopSelf()
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
            releaseMock(clearCheckpoint = false,
                arrivalChange = RouteLifecycleChange.PROVIDER_FAILURE)
            stopSelf()
        }
    }

    /** One separate system notification on arrival; the ongoing GPS channel stays quiet. */
    private fun showRouteArrivalNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return

        val view = PendingIntent.getActivity(
            this, ARRIVAL_NOTIFICATION_ID, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val completedMode = if (mode == WalkMode.FLOWERS) "尋花" else "路徑"
        val alert = Notification.Builder(this, ARRIVAL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PikminBee · $completedMode 已完成")
            .setContentText("已抵達終點，模擬位置原地停留")
            .setContentIntent(view)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(ARRIVAL_NOTIFICATION_ID, alert)
        }.onFailure { Log.w("PikminBee", "Unable to post route arrival notification", it) }
    }

    /** A previous route-complete alert must not contradict a newer user action.
     *  Expanding/hiding the floating controller or pausing must NOT clear it. */
    private fun clearOldArrivalNotification(change: RouteLifecycleChange) {
        if (!RouteSurfaceStatusPolicy.clearsPreviousArrival(change)) return
        runCatching {
            getSystemService(NotificationManager::class.java)
                .cancel(ARRIVAL_NOTIFICATION_ID)
        }.onFailure { Log.w("PikminBee", "Unable to clear stale arrival notification", it) }
    }

    private fun notification(): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PikminBee · 測試定位")
            .setContentText(LocationStatus.display.value +
                if (mode != null && phase in listOf(
                        WalkPhase.MOVING, WalkPhase.PAUSED, WalkPhase.PARKED))
                    " · 本次估算 ${LocationStatus.walkEstimatedSteps.value} 步"
                else "")
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

    private fun releaseMock(
        clearCheckpoint: Boolean = true,
        keepOverlay: Boolean = false,
        arrivalChange: RouteLifecycleChange = RouteLifecycleChange.RESTORE_REAL_GPS,
    ) {
        clearOldArrivalNotification(arrivalChange)
        mockGpsJitter.reset()
        finishEstimatedSession()
        saveEstimatedSteps()
        cancelFlowerPlanning()
        if (clearCheckpoint) journal.clear()
        lanController?.close()
        lanController = null
        if (!keepOverlay) {
            floatingControls?.dismiss()
            floatingControls = null
            LocationStatus.overlayVisible.value = false
        }
        if (providerActive) {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        providerActive = false
        LocationStatus.mockActive.value = false
        handler.removeCallbacks(tick)
        target = null
        track = emptyList()
        trackLengthMeters = 0.0
        stopThresholds = emptyList()
        LocationStatus.progress.value = WalkingProgressSnapshot()
        LocationStatus.walkEstimatedSteps.value = 0L
        LocationStatus.appliedSpeedKmh.value = 0.0
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
        floatingControls?.refreshAppearance()
        floatingControls?.onScreenChanged()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The foreground service normally survives removing the UI from Recents.
        // Save the latest position but never start an alarm/service workaround.
        if (providerActive) target?.let { journal.record(it, force = true) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // System reclaim must not dismiss a valid route-complete alert that
        // the user has not seen yet; explicit restore/stop already dismissed it.
        // Only explicit ACTION_STOP may delete a useful position checkpoint.
        releaseMock(clearCheckpoint = false,
            arrivalChange = RouteLifecycleChange.SYSTEM_SERVICE_STOP)
        serviceScope.cancel()
        super.onDestroy()
    }
}
