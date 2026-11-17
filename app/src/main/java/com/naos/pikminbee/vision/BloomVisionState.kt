package com.naos.pikminbee.vision

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Deliberately memory-only: never persist screenshots, projection tokens or raw pixels. */
object BloomVisionState {
    private val documentRevision = AtomicLong(0L)
    fun newDocumentRevision(): Long = documentRevision.incrementAndGet()

    enum class Phase { IDLE, CAPTURING }
    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val message: String = "尚未分析畫面",
        val result: BloomDetector.Result? = null,
        val origin: String? = null,
        // New selection must invalidate anchors even if detection results are structurally equal.
        val documentRevision: Long = 0L,
    )
    val state = MutableStateFlow(Snapshot())
}
