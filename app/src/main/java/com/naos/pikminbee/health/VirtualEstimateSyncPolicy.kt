package com.naos.pikminbee.health

/** Pure, testable scheduling rules for opted-in virtual step estimates. */
internal object VirtualEstimateSyncPolicy {
    const val CHECKPOINT_INTERVAL_MS = 60_000L

    /** Never create ongoing Health Connect work without explicit user opt-in. */
    fun shouldCheckpoint(startMillis: Long, nowMillis: Long, optedIn: Boolean): Boolean =
        optedIn && startMillis > 0L && nowMillis >= startMillis &&
            nowMillis - startMillis >= CHECKPOINT_INTERVAL_MS

    /** Each persisted batch advances the baseline, avoiding duplicate steps. */
    fun unsavedSteps(totalSteps: Long, savedBaselineSteps: Long): Long =
        (totalSteps - savedBaselineSteps).coerceAtLeast(0L)
}
