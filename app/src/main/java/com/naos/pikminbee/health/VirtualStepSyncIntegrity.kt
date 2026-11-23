package com.naos.pikminbee.health

import kotlinx.coroutines.CancellationException

/**
 * Pure policies shared by foreground "sync now" and WorkManager.
 * All exported estimates retain MANUAL_ENTRY provenance and stable client IDs.
 */
internal object VirtualStepSyncIntegrity {
    /** Keep each batch bounded; a full queue is handled in successive batches. */
    const val MAX_BATCH_SIZE = 50
    /** The persisted local ledger cannot contain more than 500 sessions. */
    const val MAX_DRAIN_SESSIONS = 500
    const val CLIENT_ID_PREFIX = "pikminbee-virtual-"

    fun clientRecordId(sessionId: String): String {
        require(sessionId.isNotBlank())
        return CLIENT_ID_PREFIX + sessionId
    }

    data class BatchResult(
        val acknowledged: Int,
        val optedOut: Boolean = false,
        val failure: String? = null,
    )

    /**
     * Re-check explicit opt-in and pending membership for EACH record, not just
     * at the start: the user can disable or discard a queue during a sync.
     * When a remote write succeeds but the local ack fails, retry uses the
     * same client ID so Health Connect updates that record rather than
     * identifying a new, duplicate virtual session.
     */
    suspend fun drain(
        sessions: List<EstimatedStepSession>,
        optedIn: () -> Boolean,
        stillPending: (String) -> Boolean,
        insert: suspend (EstimatedStepSession) -> Unit,
        acknowledge: (String) -> Unit,
    ): BatchResult {
        var acknowledged = 0
        for (session in sessions.take(MAX_BATCH_SIZE)) {
            if (!optedIn()) return BatchResult(acknowledged, optedOut = true)
            if (!stillPending(session.id)) continue
            try {
                insert(session)
                acknowledge(session.id)
                acknowledged++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return BatchResult(acknowledged, failure = failure.message ?: "請檢查同步權限")
            }
        }
        return BatchResult(acknowledged)
    }

    /**
     * The background worker has a bounded retry count. Writing only the first
     * 50 records per invocation could strand a 251–500-record queue forever.
     * Process all ledger records in bounded 50-record chunks within this one
     * authorised pass; stop immediately on opt-out or a remote/local failure.
     */
    suspend fun drainAll(
        sessions: List<EstimatedStepSession>,
        optedIn: () -> Boolean,
        stillPending: (String) -> Boolean,
        insert: suspend (EstimatedStepSession) -> Unit,
        acknowledge: (String) -> Unit,
    ): BatchResult {
        var total = 0
        for (batch in sessions.take(MAX_DRAIN_SESSIONS).chunked(MAX_BATCH_SIZE)) {
            val outcome = drain(batch, optedIn, stillPending, insert, acknowledge)
            total += outcome.acknowledged
            if (outcome.optedOut || outcome.failure != null)
                return outcome.copy(acknowledged = total)
        }
        return BatchResult(total)
    }

    /**
     * A readRecords time filter can return an entire StepsRecord overlapping
     * midnight. Record counts have no per-step timestamps, so *only* an
     * estimated, time-proportional share can be attributed to the selected day.
     * This is not sensor data and is NOT a Health Connect daily aggregate.
     */
    fun estimatedStepsInWindow(
        recordSteps: Long,
        recordStartMillis: Long,
        recordEndMillis: Long,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): Long {
        if (recordSteps <= 0L || recordEndMillis <= recordStartMillis ||
            windowEndMillis <= windowStartMillis) return 0L
        val overlapStart = maxOf(recordStartMillis, windowStartMillis)
        val overlapEnd = minOf(recordEndMillis, windowEndMillis)
        if (overlapEnd <= overlapStart) return 0L
        if (overlapStart <= recordStartMillis && overlapEnd >= recordEndMillis)
            return recordSteps
        // Divide cumulative allocated estimates, rather than rounding each
        // day's share independently. For 1 step across midnight, rounding both
        // halves would report 1 yesterday + 1 today (double counting).
        val duration = (recordEndMillis - recordStartMillis).toDouble()
        fun allocatedUntil(timeMillis: Long): Long {
            if (timeMillis <= recordStartMillis) return 0L
            if (timeMillis >= recordEndMillis) return recordSteps
            val elapsed = (timeMillis - recordStartMillis).toDouble()
            return (recordSteps.toDouble() * elapsed / duration).toLong()
                .coerceIn(0L, recordSteps)
        }
        return allocatedUntil(overlapEnd) - allocatedUntil(overlapStart)
    }
}
