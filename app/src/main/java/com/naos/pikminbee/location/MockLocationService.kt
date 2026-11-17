package com.naos.pikminbee.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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
import com.naos.pikminbee.lan.LanController
import com.naos.pikminbee.lan.LanStatus
import androidx.core.content.ContextCompat
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkingGeometry
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

        fun routeIntent(context: android.content.Context, track: List<Coordinate>, speed: Double,
                        mode: WalkMode = WalkMode.ROUTE, loop: Boolean = false): Intent {
            require(track.size in 2..10_000) { "Invalid walking route" }
            val packed = DoubleArray(track.size * 2) { index ->
                val point = track[index / 2]
                if (index % 2 == 0) point.latitude else point.longitude
            }
            return Intent(context, MockLocationService::class.java).setAction(ACTION_START_ROUTE)
                .putExtra(EXTRA_GEOMETRY, packed)
                .putExtra(EXTRA_SPEED, speed.coerceIn(0.5, 3.0))
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
    private var speedMetersPerSecond = 1.4
    private var loopRoute = false
    private var previousTickMillis = 0L
    private var phase = WalkPhase.IDLE
    private var mode: WalkMode? = null
    private var floatingControls: FloatingControls? = null
    private var lanController: LanController? = null
    private lateinit var journal: SessionJournal

    private val tick = object : Runnable {
        override fun run() {
            if (!providerActive) return
            val now = SystemClock.elapsedRealtime()
            val deltaSeconds = ((now - previousTickMillis) / 1000.0).coerceIn(0.0, 2.0)
            previousTickMillis = now
            if (phase == WalkPhase.MOVING && track.size > 1) {
                walkingDistanceMeters += speedMetersPerSecond * deltaSeconds
                if (loopRoute && trackLengthMeters > 0.1 && walkingDistanceMeters >= trackLengthMeters) {
                    walkingDistanceMeters %= trackLengthMeters
                }
                target = WalkingGeometry.sampleAt(track, walkingDistanceMeters)
                if (!loopRoute && walkingDistanceMeters >= trackLengthMeters - 0.1) {
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "已抵達終點 · 原地停留"
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
            floatingControls?.update(phase, mode)
            lanController?.checkNetwork()
            if (providerActive) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
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
                if (!providerActive) {
                    LocationStatus.display.value = "請先啟動模擬位置，再開啟懸浮控制"
                    return START_NOT_STICKY
                }
                if (!Settings.canDrawOverlays(this)) {
                    LocationStatus.display.value = "請先授予 PikminBee 懸浮視窗權限"
                    return START_STICKY
                }
                runCatching {
                    if (floatingControls == null) {
                        floatingControls = FloatingControls(this,
                            sendAction = { action ->
                                val actionName = when (action) {
                                    FloatingControls.PAUSE -> ACTION_PAUSE_ROUTE
                                    FloatingControls.RESUME -> ACTION_RESUME_ROUTE
                                    FloatingControls.STOP_WALK -> ACTION_STOP_ROUTE
                                    FloatingControls.RESTORE_REAL -> ACTION_STOP
                                    else -> ACTION_HIDE_OVERLAY
                                }
                                startService(command(this, actionName))
                            },
                            openMap = {
                                startActivity(Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            })
                    }
                    floatingControls?.show()
                    floatingControls?.update(phase, mode)
                    LocationStatus.overlayVisible.value = true
                }.onFailure {
                    LocationStatus.overlayVisible.value = false
                    LocationStatus.display.value = "無法顯示懸浮視窗：" + (it.message ?: "權限問題")
                }
                return START_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                floatingControls?.dismiss()
                floatingControls = null
                LocationStatus.overlayVisible.value = false
                return START_STICKY
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
                if (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED) {
                    phase = WalkPhase.PARKED
                    LocationStatus.phase.value = phase
                    LocationStatus.display.value = "步行已停止 · 保留目前位置"
                    target?.let { journal.record(it, force = true) }
                    refreshNotification()
                }
                return START_STICKY
            }
            ACTION_CLEAR_ROUTE -> {
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
                val point = runCatching {
                    Coordinate(intent.getDoubleExtra(EXTRA_LAT, Double.NaN),
                        intent.getDoubleExtra(EXTRA_LON, Double.NaN))
                }.getOrNull() ?: return START_NOT_STICKY
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
                if (WalkingGeometry.lengthMeters(planned) !in 0.1..80_000.0) {
                    LocationStatus.display.value = "步行路線無效"
                    return START_NOT_STICKY
                }
                if (!ensureProvider()) return START_NOT_STICKY
                track = planned
                trackLengthMeters = WalkingGeometry.lengthMeters(track)
                target = planned.first()
                journal.record(planned.first(), force = true)
                speedMetersPerSecond = intent.getDoubleExtra(EXTRA_SPEED, 1.4).coerceIn(0.5, 3.0)
                walkingDistanceMeters = 0.0
                loopRoute = intent.getBooleanExtra(EXTRA_LOOP, false)
                mode = runCatching {
                    WalkMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "")
                }.getOrDefault(WalkMode.ROUTE)
                phase = WalkPhase.MOVING
                LocationStatus.phase.value = phase
                LocationStatus.mode.value = mode
                LocationStatus.geometry.value = track
                LocationStatus.display.value = if (mode == WalkMode.FLOWERS) "尋花步行中" else "沿道路步行中"
                scheduleTicks()
                refreshNotification()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
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
        super.onDestroy()
    }
}
