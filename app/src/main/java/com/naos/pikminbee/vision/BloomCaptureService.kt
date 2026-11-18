package com.naos.pikminbee.vision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.naos.pikminbee.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Per-session, user-consented read-only screen projection.
 *
 * One consent -> one getMediaProjection -> one createVirtualDisplay. No screenshot files,
 * game input, hidden APIs, capture-protection bypass, or background auto-start.
 */
class BloomCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.naos.pikminbee.vision.START"
        const val ACTION_STOP = "com.naos.pikminbee.vision.STOP"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_CONSENT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "pikminbee_bloom_vision"
        private const val NOTIFICATION_ID = 413

        fun startIntent(context: Context, code: Int, data: Intent): Intent =
            Intent(context, BloomCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, code)
                .putExtra(EXTRA_CONSENT_DATA, data)

        fun stopIntent(context: Context): Intent =
            Intent(context, BloomCaptureService::class.java).setAction(ACTION_STOP)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val working = AtomicBoolean(false)
    private var started = false
    private var lastFrameAt = 0L
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val frameThread = HandlerThread("PikminBee-vision-frames")
    private lateinit var handler: Handler
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            BloomVisionState.state.value = BloomVisionState.state.value.copy(
                message = "螢幕分享已由系統或使用者結束；如要重開必須重新授權"
            )
            stopCapture()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            handler.post { resizeCapture(width, height) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        frameThread.start()
        handler = Handler(frameThread.looper)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PikminBee 花朵辨識畫面分享",
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (started || projection != null) return START_NOT_STICKY
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_CONSENT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_CONSENT_DATA)
                }
                if (code != android.app.Activity.RESULT_OK || data == null) {
                    BloomVisionState.state.value = BloomVisionState.Snapshot(
                        message = "未取得畫面分享授權，沒有擷取任何資料")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                try {
                    // Must be in a mediaProjection foreground service BEFORE getMediaProjection.
                    if (Build.VERSION.SDK_INT >= 29) {
                        ServiceCompat.startForeground(
                            this, NOTIFICATION_ID, notification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                        )
                    } else startForeground(NOTIFICATION_ID, notification())

                    val manager = getSystemService(MediaProjectionManager::class.java)
                    val capture = manager.getMediaProjection(code, data)
                        ?: throw IllegalStateException("系統未授權螢幕分享")
                    projection = capture
                    capture.registerCallback(projectionCallback, handler)
                    val (w, h) = dimensions()
                    densityDpi = resources.displayMetrics.densityDpi
                    val surfaceReader = createReader(w, h)
                    reader = surfaceReader
                    // Exactly one virtual display per fresh consent token.
                    display = capture.createVirtualDisplay(
                        "PikminBee Flower Preview",
                        w, h, densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surfaceReader.surface, null, handler,
                    )
                    started = true
                    BloomVisionState.state.value = BloomVisionState.Snapshot(
                        phase = BloomVisionState.Phase.CAPTURING,
                        message = "只讀取使用者已授權的畫面，每約 3 秒分析一次；不儲存圖片",
                        origin = "系統授權的螢幕分享",
                    )
                } catch (e: Exception) {
                    BloomVisionState.state.value = BloomVisionState.Snapshot(
                        message = "無法開始畫面辨識：" + (e.message ?: "系統拒絕擷取"))
                    stopCapture()
                }
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY // Never silently resume a projection after process reclaim.
    }

    private fun dimensions(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else null
        return max(16, bounds?.width() ?: metrics.widthPixels) to
            max(16, bounds?.height() ?: metrics.heightPixels)
    }

    private fun createReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also {
            it.setOnImageAvailableListener({ source -> processNextFrame(source) }, handler)
        }

    private fun resizeCapture(width: Int, height: Int) {
        if (!started || display == null || width == reader?.width && height == reader?.height) return
        runCatching {
            val next = createReader(width, height)
            display?.resize(width, height, densityDpi)
            display?.setSurface(next.surface)
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
            reader = next
        }.onFailure {
            BloomVisionState.state.value = BloomVisionState.state.value.copy(
                message = "畫面尺寸變化後無法繼續辨識：" + it.message)
            stopCapture()
        }
    }

    private fun processNextFrame(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (!started || working.get() || now - lastFrameAt < 3_000L) return
            if (!working.compareAndSet(false, true)) return
            lastFrameAt = now
            val frame = frameFromImage(image)
            scope.launch {
                try {
                    val result = BloomDetector.analyse(frame)
                    if (started) {
                        BloomVisionState.state.value = BloomVisionState.Snapshot(
                            phase = BloomVisionState.Phase.CAPTURING,
                            message = if (result.mostlyBlank) result.note else
                                "本次找到 ${result.hits.size} 個畫面候選；尚未驗證真實準確率與世界座標",
                            result = result,
                            origin = "系統授權的螢幕分享",
                        )
                    }
                } catch (e: Exception) {
                    if (started) BloomVisionState.state.value = BloomVisionState.state.value.copy(
                        message = "畫面分析失敗：" + (e.message ?: "格式不支援"))
                } finally {
                    working.set(false)
                }
            }
        } catch (e: Exception) {
            working.set(false)
            BloomVisionState.state.value = BloomVisionState.state.value.copy(
                message = "無法讀取畫面：" + (e.message ?: "畫面尚未準備"))
        } finally {
            image.close()
        }
    }

    private fun frameFromImage(image: Image): BloomDetector.Frame {
        val plane = image.planes.firstOrNull() ?: throw IllegalArgumentException("擷取畫面沒有像素資料")
        if (plane.pixelStride < 4) throw IllegalArgumentException("目前的畫面像素格式不支援")
        val width = image.width
        val height = image.height
        val scale = min(1.0, min(480.0 / width, 850.0 / height))
        val outW = max(16, (width * scale).toInt())
        val outH = max(16, (height * scale).toInt())
        val output = IntArray(outW * outH)
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        for (y in 0 until outH) {
            val sy = (y.toLong() * height / outH).toInt().coerceAtMost(height - 1)
            for (x in 0 until outW) {
                val sx = (x.toLong() * width / outW).toInt().coerceAtMost(width - 1)
                val offset = sy * rowStride + sx * plane.pixelStride
                if (offset + 3 >= buffer.limit()) throw IllegalArgumentException("畫面緩衝區已損壞")
                val red = buffer.get(offset).toInt() and 255
                val green = buffer.get(offset + 1).toInt() and 255
                val blue = buffer.get(offset + 2).toInt() and 255
                output[y * outW + x] = -0x1000000 or (red shl 16) or (green shl 8) or blue
            }
        }
        return BloomDetector.Frame(outW, outH, output)
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 13, stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 14, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("PikminBee · 畫面花朵辨識中")
            .setContentText("使用者授權的畫面擷取 · 按停止可立即結束")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    this, android.R.drawable.ic_menu_close_clear_cancel),
                "停止畫面擷取", stop,
            ).build())
            .build()
    }

    private fun stopCapture() {
        started = false
        // Do not retain raw image buffers, a valid MediaProjection token, or any service after stop.
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        display = null
        reader?.close()
        reader = null
        val current = projection
        projection = null
        current?.unregisterCallback(projectionCallback)
        current?.stop()
        val snapshot = BloomVisionState.state.value
        BloomVisionState.state.value = snapshot.copy(
            phase = BloomVisionState.Phase.IDLE,
            message = if (snapshot.message.contains("系統或使用者結束")) snapshot.message
                else "畫面擷取已停止；分析結果僅保留在本次 App 記憶體中",
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        display?.release()
        reader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        display = null
        reader = null
        projection = null
        frameThread.quitSafely()
        scope.cancel()
        BloomVisionState.state.value = BloomVisionState.state.value.copy(
            phase = BloomVisionState.Phase.IDLE)
        super.onDestroy()
    }
}
