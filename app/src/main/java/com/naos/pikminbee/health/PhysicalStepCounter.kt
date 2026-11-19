package com.naos.pikminbee.health

import kotlin.math.ceil

/**
 * Android TYPE_STEP_COUNTER is the cumulative count *since reboot*, not a daily count.
 * Start from the first observed baseline, handle reboot/reset, discard impossible spikes,
 * and never fabricate steps during a pause or when the sensor is not registered.
 */
class PhysicalStepCounter {
    private var previous: Long? = null
    private var previousTimestampNanos = 0L

    fun observe(totalSinceBoot: Float, eventTimestampNanos: Long): Long {
        if (!totalSinceBoot.isFinite() || totalSinceBoot < 0f || eventTimestampNanos <= 0)
            return 0
        val current = totalSinceBoot.toLong()
        val previousValue = previous
        val previousTime = previousTimestampNanos
        previous = current
        previousTimestampNanos = eventTimestampNanos
        if (previousValue == null || eventTimestampNanos <= previousTime ||
            current <= previousValue) return 0L
        val elapsedSeconds = (eventTimestampNanos - previousTime) / 1_000_000_000.0
        // 5 steps/s is a generous ceiling, plus slack for sensor batching/jitter.
        val plausibleMax = ceil(elapsedSeconds * 5 + 12).toLong()
        val delta = current - previousValue
        return if (delta <= plausibleMax) delta else 0L
    }
}

data class PhysicalStepsSnapshot(
    val date: String = "",
    val todaySteps: Long = 0,
    val tracking: Boolean = false,
    val note: String = "尚未啟用手機感測器計步",
)

object PhysicalStepsStatus {
    val state = kotlinx.coroutines.flow.MutableStateFlow(PhysicalStepsSnapshot())
}
