package com.naos.pikminbee.location

import android.content.Context
import android.os.SystemClock
import com.naos.pikminbee.domain.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow

/** Device-private, short-lived checkpoint. Not a request to auto-run after force-stop or reboot. */
data class LocationCheckpoint(val coordinate: Coordinate, val recordedAtMillis: Long)

object CheckpointStatus {
    val last = MutableStateFlow<LocationCheckpoint?>(null)
}

object CheckpointCodec {
    const val MAX_AGE_MS = 12L * 60 * 60 * 1000
    private const val VERSION = "v1"

    fun encode(checkpoint: LocationCheckpoint): String =
        listOf(
            VERSION, checkpoint.coordinate.latitude.toString(),
            checkpoint.coordinate.longitude.toString(),
            checkpoint.recordedAtMillis.toString(),
        ).joinToString("|")

    fun decode(value: String?, nowMillis: Long): LocationCheckpoint? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 4 || parts[0] != VERSION) return null
        val coordinate = runCatching {
            Coordinate(parts[1].toDouble(), parts[2].toDouble())
        }.getOrNull() ?: return null
        val timestamp = parts[3].toLongOrNull() ?: return null
        val age = nowMillis - timestamp
        if (age !in 0..MAX_AGE_MS || timestamp <= 0L) return null
        return LocationCheckpoint(coordinate, timestamp)
    }
}

/**
 * Last simulated fix persisted at most once every five seconds (immediately on first start).
 * An explicit restore-real-GPS command clears it. Silent restarts NEVER resume walking.
 */
class SessionJournal(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val prefs = context.getSharedPreferences("pikminbee_last_mock_fix", Context.MODE_PRIVATE)
    private var lastPersistedAtElapsed = -10_000L

    fun read(): LocationCheckpoint? {
        val checkpoint = CheckpointCodec.decode(prefs.getString("checkpoint", null), clockMillis())
        CheckpointStatus.last.value = checkpoint
        return checkpoint
    }

    fun record(point: Coordinate, force: Boolean = false) {
        val nowElapsed = elapsedMillis()
        if (!force && nowElapsed - lastPersistedAtElapsed < 5_000L) return
        lastPersistedAtElapsed = nowElapsed
        val checkpoint = LocationCheckpoint(point, clockMillis())
        prefs.edit().putString("checkpoint", CheckpointCodec.encode(checkpoint)).apply()
        CheckpointStatus.last.value = checkpoint
    }

    fun clear() {
        // Avoid resurrecting a session after the user explicitly requested real GPS.
        prefs.edit().remove("checkpoint").commit()
        CheckpointStatus.last.value = null
    }
}
