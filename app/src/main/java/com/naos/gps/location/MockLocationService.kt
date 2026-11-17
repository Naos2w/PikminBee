package com.naos.gps.location

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
import androidx.core.content.ContextCompat
import com.naos.gps.domain.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow

/** Single opt-in mock-location source for the user's own Android test device. */
object LocationStatus {
    val display = MutableStateFlow("真實定位")
    val selected = MutableStateFlow<Coordinate?>(null)
}

class MockLocationService : Service() {
    companion object {
        const val ACTION_TELEPORT = "com.naos.gps.action.TELEPORT"
        const val ACTION_STOP = "com.naos.gps.action.STOP_MOCK"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        private const val CHANNEL = "naos_gps_active"
        private const val NOTIFICATION_ID = 301

        fun teleportIntent(context: android.content.Context, coordinate: Coordinate): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_TELEPORT)
                .putExtra(EXTRA_LAT, coordinate.latitude)
                .putExtra(EXTRA_LON, coordinate.longitude)

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: LocationManager
    private var active = false
    private var target: Coordinate? = null
    private val tick = object : Runnable {
        override fun run() {
            target?.let { pushFix(it) }
            if (active) handler.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "NaosGPS 模擬位置", NotificationManager.IMPORTANCE_LOW
        ))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseMock()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_TELEPORT) return START_NOT_STICKY

        val coordinate = runCatching {
            Coordinate(intent.getDoubleExtra(EXTRA_LAT, Double.NaN),
                intent.getDoubleExtra(EXTRA_LON, Double.NaN))
        }.getOrNull() ?: return START_NOT_STICKY

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            LocationStatus.display.value = "缺少精確定位權限"
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startForeground(NOTIFICATION_ID, notification())
            if (!active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val properties = android.location.provider.ProviderProperties.Builder()
                        .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
                        .setHasAltitudeSupport(true)
                        .setHasSpeedSupport(true)
                        .setHasBearingSupport(true)
                        .build()
                    manager.addTestProvider(LocationManager.GPS_PROVIDER, properties)
                } else {
                    registerLegacyProvider()
                }
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
            active = true
            target = coordinate
            handler.removeCallbacks(tick)
            tick.run()
            LocationStatus.selected.value = coordinate
            LocationStatus.display.value = "模擬位置執行中"
            START_STICKY
        } catch (ex: Exception) {
            LocationStatus.display.value = "無法啟動：請在開發者選項選取 NaosGPS"
            releaseMock()
            stopSelf()
            START_NOT_STICKY
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("WrongConstant")
    private fun registerLegacyProvider() {
        manager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, false, false, false,
            true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE
        )
    }

    private fun pushFix(coordinate: Coordinate) {
        try {
            val fix = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = coordinate.latitude
                longitude = coordinate.longitude
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                altitude = 0.0
                speed = 0f
                bearing = 0f
            }
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix)
        } catch (ex: Exception) {
            LocationStatus.display.value = "模擬位置已中斷"
            releaseMock()
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("NaosGPS · 測試定位")
            .setContentText("模擬位置運作中 · 點擊通知的停止以恢復真實 GPS")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "停止模擬", stop
            ).build())
            .build()
    }

    private fun releaseMock() {
        active = false
        handler.removeCallbacks(tick)
        target = null
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        LocationStatus.selected.value = null
        LocationStatus.display.value = "真實定位"
    }

    override fun onDestroy() {
        releaseMock()
        super.onDestroy()
    }
}
