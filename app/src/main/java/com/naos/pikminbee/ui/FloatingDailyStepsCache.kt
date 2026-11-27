package com.naos.pikminbee.ui

import java.time.Instant
import java.time.ZoneId

/**
 * Cache identity includes time-zone *and* local date. A date-only key can reuse
 * yesterday's HC midnight boundary after the time zone changes on the same day.
 */
internal data class FloatingDayKey(val date: String, val timeZoneId: String) {
    companion object {
        fun at(instant: Instant, zone: ZoneId): FloatingDayKey =
            FloatingDayKey(instant.atZone(zone).toLocalDate().toString(), zone.id)

        fun now(): FloatingDayKey = at(Instant.now(), ZoneId.systemDefault())
    }
}

/**
 * Health Connect's daily aggregate is independently read, never merged with
 * on-device sensor or route-estimated steps.
 *
 * Elapsed time comes from Android's monotonic clock, not wall-clock milliseconds.
 * A new period or a forced read invalidates old data before the new result arrives.
 * A failed or permission-denied read clears the count and has a bounded retry delay.
 */
internal class FloatingDailyStepsCache(
    private val freshnessMillis: Long = 60_000L,
    private val retryMillis: Long = 15_000L,
) {
    init {
        require(freshnessMillis > 0 && retryMillis > 0)
    }

    private var period: FloatingDayKey? = null
    private var lastAttemptElapsed: Long? = null
    private var lastSuccessElapsed: Long? = null
    private var aggregate: Long? = null
    private var revision = 0L

    /** @return true if an outstanding read for the old period should be cancelled. */
    fun ensurePeriod(current: FloatingDayKey): Boolean {
        if (period == current) return false
        invalidate()
        period = current
        return true
    }

    /** Null is unavailable, never convert unknown to a fabricated zero. */
    fun currentValue(current: FloatingDayKey, elapsed: Long): Long? =
        aggregate?.takeIf {
            period == current && fresh(lastSuccessElapsed, elapsed, freshnessMillis)
        }

    /** Token for the newest request; null means the request is still rate-limited. */
    fun begin(current: FloatingDayKey, elapsed: Long, force: Boolean = false): Long? {
        ensurePeriod(current)
        val throttle = if (aggregate == null) retryMillis else freshnessMillis
        if (!force && fresh(lastAttemptElapsed, elapsed, throttle)) return null
        if (force) {
            aggregate = null
            lastSuccessElapsed = null
        }
        lastAttemptElapsed = elapsed
        return ++revision
    }

    /** Old completions must not reinstate a stale total after a new day, zone or opening. */
    fun complete(
        token: Long,
        requested: FloatingDayKey,
        current: FloatingDayKey,
        elapsed: Long,
        value: Long?,
    ): Boolean {
        if (token != revision || period != requested || current != requested) return false
        aggregate = value?.takeIf { it >= 0L }
        lastSuccessElapsed = if (aggregate != null) elapsed else null
        return true
    }

    fun invalidate() {
        revision++
        period = null
        aggregate = null
        lastAttemptElapsed = null
        lastSuccessElapsed = null
    }

    private fun fresh(previous: Long?, elapsed: Long, maxAge: Long): Boolean =
        previous != null && elapsed >= previous && elapsed - previous < maxAge
}
