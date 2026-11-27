package com.naos.pikminbee.health

/**
 * Accumulate only distance-derived route estimates while moving.
 * The monotonic clock controls the one-minute checkpoint cadence; each
 * closed interval retains its own wall-clock boundaries on pause or stop.
 * Failed local persistence leaves the closed interval queued in memory.
 */
internal class RouteEstimateCheckpoint {
    data class Chunk(val startMillis: Long, val endMillis: Long, val steps: Long)
    private data class Active(val startMillis: Long, val endMillis: Long,
                              val startElapsed: Long, val steps: Long)
    private var active: Active? = null
    private val ready = ArrayDeque<Chunk>()

    fun recordMovement(startMillis: Long, endMillis: Long, elapsedMillis: Long,
                       deltaSteps: Long) {
        if (startMillis <= 0L || endMillis <= startMillis || deltaSteps < 0L) return
        // Do not merge across a backwards wall-clock jump or a long interruption.
        active?.let {
            if (endMillis <= it.endMillis || startMillis - it.endMillis > 3_000L) finish()
        }
        val interval = active ?: Active(startMillis, endMillis,
            elapsedMillis - (endMillis - startMillis), 0L)
        active = interval.copy(endMillis = endMillis, steps = interval.steps + deltaSteps)
        if (elapsedMillis - interval.startElapsed >= 60_000L) finish()
    }

    fun finish() {
        active?.let {
            if (it.steps > 0L && it.endMillis > it.startMillis)
                ready.addLast(Chunk(it.startMillis, it.endMillis, it.steps))
        }
        active = null
    }

    /** Remove a chunk only after the local store has acknowledged it. */
    fun drain(store: (Chunk) -> Boolean): Int {
        var saved = 0
        while (ready.isNotEmpty()) {
            if (!store(ready.first())) break
            ready.removeFirst()
            saved++
        }
        return saved
    }
}
