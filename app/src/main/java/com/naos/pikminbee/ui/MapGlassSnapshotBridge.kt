package com.naos.pikminbee.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Connects MapLibre / Google Maps to one bounded optical-snapshot scheduler.
 * The expensive SDK snapshot is never called on every camera animation frame.
 * Callbacks return to the UI thread, and any late callback after disposal or
 * timeout is ignored by the capture gate.
 *
 * Sampling a live native-map view is still asynchronous; on devices whose map
 * renderer cannot produce live snapshots, the existing frosted fallback remains.
 */
internal class MapGlassSnapshotBridge(
    private val isAttached: () -> Boolean,
    private val capture: (((Bitmap?) -> Unit) -> Unit),
    private val deliver: (Bitmap?) -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    private val gate = MapGlassCaptureGate()
    private val main = Handler(Looper.getMainLooper())
    private var disposed = false
    private var idleRecoveryPending = false

    fun cameraStarted() {
        captureId(gate.cameraStarted(now()))
    }

    fun cameraMoved() {
        val t = now()
        if (gate.shouldClearStaleMovingBitmap(t)) deliver(null)
        captureId(gate.cameraMoved(t))
    }

    fun cameraIdle() {
        val id = gate.cameraIdle(now())
        captureId(id)
        if (id == null && !idleRecoveryPending) {
            // If an earlier snapshot never calls back, a final idle frame still
            // needs a chance to replace it. Only one delayed retry is posted.
            idleRecoveryPending = true
            main.postDelayed({
                if (!disposed && idleRecoveryPending) {
                    idleRecoveryPending = false
                    captureId(gate.cameraIdle(now()))
                }
            }, 1700L)
        }
    }

    private fun captureId(id: Long?) {
        if (id == null || disposed) return
        if (!isAttached()) {
            onCapture(id, null)
            return
        }
        try {
            capture { bitmap ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onCapture(id, bitmap)
                } else {
                    main.post { onCapture(id, bitmap) }
                }
            }
        } catch (_: Exception) {
            onCapture(id, null)
        }
    }

    private fun onCapture(id: Long, bitmap: Bitmap?) {
        if (disposed) return
        val result = gate.complete(id, now(), bitmap != null && isAttached())
        if (result.accepted) {
            idleRecoveryPending = false
            deliver(bitmap)
        }
        if (result.followUpId != null) {
            idleRecoveryPending = false
            captureId(result.followUpId)
        }
    }

    fun close() {
        if (disposed) return
        disposed = true
        idleRecoveryPending = false
        gate.close()
        main.removeCallbacksAndMessages(null)
        deliver(null)
    }
}
