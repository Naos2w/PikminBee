package com.naos.pikminbee.vision

/**
 * Only session-local, non-image metadata appears in the capture diagnostics.
 * A running MediaProjection/VirtualDisplay is not proof that frames arrived.
 */
internal object BloomCaptureDiagnosticPolicy {
    fun label(analysedFrames: Long, visible: Boolean?): String {
        val visibility = when (visible) {
            true -> "分享區域可見"
            false -> "分享區域目前不可見"
            null -> "分享區域狀態未回報"
        }
        return "已分析 ${analysedFrames.coerceAtLeast(0L)} 張影格 · $visibility"
    }

    fun frameMessage(result: BloomDetector.Result, visible: Boolean?): String = when {
        visible == false ->
            "Android 回報分享的內容目前不可見；請檢查授權的分享範圍"
        result.mostlyBlank -> result.note
        else ->
            "本次找到 ${result.hits.size} 個畫面候選；尚未驗證真實準確率與世界座標"
    }
}
