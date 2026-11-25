package com.naos.pikminbee.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.naos.pikminbee.MainActivity

/**
 * Explicit, optional LAN listener. The browser never starts a second GPS engine.
 * Runs as an Android connected-device foreground service so web control remains
 * reachable when the map is backgrounded, without requiring mock GPS to start.
 */
class LanControlService : Service() {
    companion object {
        const val ENABLE = "com.naos.pikminbee.lan.ENABLE"
        const val DISABLE = "com.naos.pikminbee.lan.DISABLE"
        const val APPROVE = "com.naos.pikminbee.lan.APPROVE"
        const val DENY = "com.naos.pikminbee.lan.DENY"
        const val DISCONNECT = "com.naos.pikminbee.lan.DISCONNECT"
        const val SHARE_PHYSICAL = "com.naos.pikminbee.lan.SHARE_PHYSICAL"
        const val REVOKE_PHYSICAL = "com.naos.pikminbee.lan.REVOKE_PHYSICAL"
        private const val CHANNEL = "pikminbee_remote_control"
        private const val NOTIFICATION_ID = 2097

        fun command(context: Context, action: String): Intent =
            Intent(context, LanControlService::class.java).setAction(action)

        private const val EXTRA_SHARE_PHYSICAL = "share_physical_on_approval"
        private const val EXTRA_APPROVAL_ID = "pending_approval_id"

        /** Only the on-phone approval surface calls this; the service is not exported. */
        fun approveCommand(context: Context, approvalId: String, sharePhysical: Boolean): Intent =
            command(context, APPROVE).putExtra(EXTRA_APPROVAL_ID, approvalId)
                .putExtra(EXTRA_SHARE_PHYSICAL, sharePhysical)

        /** Call from visible user gesture; starting in the background is prohibited. */
        fun enable(context: Context) =
            ContextCompat.startForegroundService(context, command(context, ENABLE))
    }

    private val handler = Handler(Looper.getMainLooper())
    private var controller: LanController? = null
    private val networkWatch = object : Runnable {
        override fun run() {
            controller?.checkNetwork()
            if (!LanStatus.display.value.active) {
                stopSelf()
            } else handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL, "PikminBee 電腦控制",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ENABLE -> {
                if (!LanStatus.display.value.active) LanStatus.display.value = LanDisplay(
                    note = "正在啟動 Wi-Fi 控制", connection = LanConnection(LanConnectionPhase.STARTING))
                // Foreground notification must be created promptly after
                // startForegroundService even if Wi-Fi is not available.
                runCatching {
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                    )
                }.onFailure {
                    LanStatus.display.value = LanDisplay(note =
                        "電腦控制服務無法啟動：" + (it.message ?: "系統限制"),
                        connection = LanConnection(LanConnectionPhase.ERROR))
                    stopSelf()
                    return START_NOT_STICKY
                }
                val active = controller ?: LanController(this).also { controller = it }
                val result = active.start()
                if (!LanStatus.display.value.active) {
                    // Preserve the actionable network failure for the Settings UI.
                    controller?.close()
                    controller = null
                    LanStatus.display.value = LanDisplay(note = result,
                        connection = LanConnection(LanConnectionPhase.ERROR))
                    stopSelf()
                } else {
                    handler.removeCallbacks(networkWatch)
                    handler.postDelayed(networkWatch, 1_000L)
                }
                START_NOT_STICKY
            }
            APPROVE -> {
                controller?.approvePending(
                    intent.getBooleanExtra(EXTRA_SHARE_PHYSICAL, false),
                    intent.getStringExtra(EXTRA_APPROVAL_ID),
                )
                START_NOT_STICKY
            }
            DENY -> {
                controller?.denyPending()
                START_NOT_STICKY
            }
            DISCONNECT -> {
                controller?.disconnectBrowser()
                START_NOT_STICKY
            }
            SHARE_PHYSICAL -> {
                controller?.approvePhysicalSharing()
                START_NOT_STICKY
            }
            REVOKE_PHYSICAL -> {
                controller?.revokePhysicalSharing()
                START_NOT_STICKY
            }
            DISABLE -> {
                shutdown()
                stopSelf()
                START_NOT_STICKY
            }
            // Never implicitly restart a LAN server after a killed process.
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 27, Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_WIFI_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 28, command(this, DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("PikminBee · 電腦網頁控制")
            .setContentText("同一私人網路配對後可從瀏覽器控制 · 點擊管理")
            .setContentIntent(open)
            .addAction(0, "結束網頁控制", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun shutdown() {
        handler.removeCallbacks(networkWatch)
        controller?.close()
        controller = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
