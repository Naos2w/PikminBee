package com.naos.pikminbee.ui

/**
 * Bounded, provider-agnostic camera snapshot scheduling.
 *
 * A moving map provides a new bitmap at most every [minimumIntervalMs] rather
 * than at every camera frame. Only one snapshot is in flight, with a timeout
 * that permits recovery if an SDK never invokes its callback. Superseded
 * callbacks are rejected and an idle frame takes precedence over a moving one.
 *
 * This class deliberately does not draw or own Bitmap objects.
 */
internal class MapGlassCaptureGate(
    private val minimumIntervalMs: Long = 320L,
    private val captureTimeoutMs: Long = 1600L,
    private val maximumAgeMs: Long = 1100L,
) {
    init {
        require(minimumIntervalMs > 0L)
        require(captureTimeoutMs > minimumIntervalMs)
        require(maximumAgeMs > minimumIntervalMs)
    }

    data class Completion(val accepted: Boolean, val followUpId: Long? = null)

    private var closed = false
    private var moving = false
    private var pendingIdle = false
    private var pendingId: Long? = null
    private var lastRequestedAt: Long? = null
    private var lastAcceptedAt: Long? = null
    private var expiredShown = false
    private var nextId = 0L

    fun cameraStarted(nowMs: Long): Long? {
        moving = true
        // A new gesture can interrupt an idle transition; its pending final
        // frame must no longer take priority over the current moving camera.
        pendingIdle = false
        return request(nowMs, force = true)
    }

    fun cameraMoved(nowMs: Long): Long? =
        if (moving) request(nowMs, force = false) else null

    fun cameraIdle(nowMs: Long): Long? {
        moving = false
        pendingIdle = true
        return request(nowMs, force = true)?.also { pendingIdle = false }
    }

    private fun request(nowMs: Long, force: Boolean): Long? {
        if (closed) return null
        if (pendingId != null && nowMs - (lastRequestedAt ?: nowMs) < captureTimeoutMs)
            return null
        if (!force && lastRequestedAt != null &&
            nowMs - lastRequestedAt!! < minimumIntervalMs)
            return null
        val id = ++nextId
        pendingId = id
        lastRequestedAt = nowMs
        return id
    }

    /**
     * A callback from a request that the watchdog superseded must never be
     * applied. A moving-frame result received after cameraIdle is also ignored
     * in favour of the latest stationary frame.
     */
    fun complete(id: Long, nowMs: Long, hasBitmap: Boolean): Completion {
        if (closed || pendingId != id) return Completion(false)
        pendingId = null
        val accept = hasBitmap && !pendingIdle
        if (accept) {
            lastAcceptedAt = nowMs
            expiredShown = false
        }
        if (pendingIdle) {
            pendingIdle = false
            return Completion(false, request(nowMs, force = true))
        }
        return Completion(accept)
    }

    /** When the renderer has stalled, remove only genuinely outdated optics. */
    fun shouldClearStaleMovingBitmap(nowMs: Long): Boolean {
        val acceptedAt = lastAcceptedAt ?: return false
        if (!moving || closed || expiredShown || nowMs - acceptedAt < maximumAgeMs)
            return false
        expiredShown = true
        return true
    }

    fun close() {
        closed = true
        pendingId = null
        pendingIdle = false
        moving = false
    }
}
