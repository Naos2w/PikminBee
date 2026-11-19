package com.naos.pikminbee.health

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.naos.pikminbee.MainActivity
import java.time.LocalDate

/**
 * Legacy fallback only: user-started foreground sensor recorder. It never tries
 * to run alongside Android 14 + Ext20 Health Connect native step recording.
 * Runtime ACTIVITY_RECOGNITION is mandatory. Force-stop still stops all tracking.
 */
class PhysicalStepTrackingService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.naos.pikminbee.action.START_PHYSICAL_STEPS"
        const val ACTION_STOP = "com.naos.pikminbee.action.STOP_PHYSICAL_STEPS"
        private const val CHANNEL = "pikminbee_physical_steps"
        private const val NOTIFICATION_ID = 304
        fun command(context: android.content.Context, action: String) =
            Intent(context, PhysicalStepTrackingService::class.java).setAction(action)
    }

    private lateinit var manager: SensorManager
    private lateinit var ledger: PhysicalStepLedger
    private var registered = false
    private var lastSampleWallMillis = 0L
    private var lastFlushWallMillis = 0L
    private var lastNotificationMillis = 0L
    private var stepCounter = PhysicalStepCounter()
    private val handler = Handler(Looper.getMainLooper())

    private val flushTask = object : Runnable {
        override fun run() {
            if (!registered) return
            flush()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(SensorManager::class.java)
        ledger = PhysicalStepLedger(this)
        PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
            LocalDate.now().toString(), ledger.todaysCount(), false,
            "真實計步未啟動"
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "PikminBee 真實計步", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            finishTracking()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (HealthConnectRealSteps.nativeDeviceStepsSupported()) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), false,
                "這台手機可由 Health Connect 原生記步；不啟動重複感測服務"
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), false,
                "請先授予 Android 身體活動辨識權限"
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val hardware = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (hardware == null) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), false,
                "手機沒有硬體步數計數器，請使用其他 Health Connect 來源"
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else startForeground(NOTIFICATION_ID, notification)
            if (!registered) {
                stepCounter = PhysicalStepCounter()
                registered = manager.registerListener(this, hardware, SensorManager.SENSOR_DELAY_NORMAL)
                if (!registered) throw IllegalStateException("裝置拒絕註冊步數感測器")
                lastSampleWallMillis = 0L
                lastFlushWallMillis = System.currentTimeMillis()
                handler.postDelayed(flushTask, 60_000L)
            }
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), true,
                "手機實際步數感測器正在記錄"
            )
        } catch (error: Exception) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), false,
                "無法啟動真實計步：" + (error.message ?: "請檢查背景權限")
            )
            finishTracking()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val now = System.currentTimeMillis()
        val delta = stepCounter.observe(event.values.firstOrNull() ?: return, event.timestamp)
        if (delta > 0) {
            runCatching { ledger.addFromSensor(delta, now, lastSampleWallMillis) }
                .onFailure { error ->
                    PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                        LocalDate.now().toString(), ledger.todaysCount(), true,
                        "計步保存失敗：" + (error.message ?: "儲存空間或紀錄上限")
                    )
                }
        }
        lastSampleWallMillis = now
        val state = PhysicalStepsStatus.state.value
        if (!state.note.startsWith("計步保存失敗")) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), true,
                "手機實際步數感測器正在記錄"
            )
        }
        if (now - lastNotificationMillis > 60_000) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification())
            lastNotificationMillis = now
        }
    }

    private fun notification(): Notification {
        val intent = Intent(this, MainActivity::class.java).putExtra(
            MainActivity.EXTRA_OPEN_SETTINGS, true
        )
        val pending = PendingIntent.getActivity(this, 304, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 305, command(this, ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("PikminBee · 真實手機計步")
            .setContentText("本次啟用 · 今日觀察 " + ledger.todaysCount() + " 步")
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_media_pause, "停止計步", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun flush() {
        val result = runCatching { ledger.flushPending() }.getOrDefault(false)
        if (!result) {
            PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
                LocalDate.now().toString(), ledger.todaysCount(), true,
                "感測步數待同步紀錄已達上限，請在設定管理"
            )
        } else if (ledger.optIn()) {
            PhysicalStepsSyncWorker.enqueue(this)
        }
        lastFlushWallMillis = System.currentTimeMillis()
    }

    private fun finishTracking() {
        if (registered) manager.unregisterListener(this)
        registered = false
        handler.removeCallbacks(flushTask)
        flush()
        PhysicalStepsStatus.state.value = PhysicalStepsSnapshot(
            LocalDate.now().toString(), ledger.todaysCount(), false,
            "計步已停止；重新啟動前不會收集新的感測步數"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        finishTracking()
        super.onDestroy()
    }
}
