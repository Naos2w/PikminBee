package com.naos.pikminbee.vision

import kotlinx.coroutines.flow.MutableStateFlow

/** Deliberately memory-only: never persist screenshots, projection tokens or raw pixels. */
object BloomVisionState {
    enum class Phase { IDLE, CAPTURING }
    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val message: String = "尚未分析畫面",
        val result: BloomDetector.Result? = null,
        val origin: String? = null,
    )
    val state = MutableStateFlow(Snapshot())
}
