package com.naos.pikminbee.health

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Stable source-specific records; never use the virtual route's record IDs or metadata. */
data class PhysicalStepInterval(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val steps: Long,
    val synced: Boolean = false,
) {
    init {
        require(id.isNotBlank() && id.length <= 100)
        require(startMillis > 0 && endMillis > startMillis)
        require(steps in 1..100_000)
    }
}

object PhysicalStepIntervalCodec {
    fun encode(items: List<PhysicalStepInterval>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().put("id", it.id)
                .put("start", it.startMillis).put("end", it.endMillis)
                .put("steps", it.steps).put("synced", it.synced))
        }
    }.toString()

    fun decode(value: String): List<PhysicalStepInterval> = runCatching {
        val array = JSONArray(value)
        require(array.length() <= 500)
        (0 until array.length()).mapNotNull {
            runCatching {
                val item = array.getJSONObject(it)
                PhysicalStepInterval(item.getString("id"), item.getLong("start"),
                    item.getLong("end"), item.getLong("steps"),
                    item.optBoolean("synced", false))
            }.getOrNull()
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

/**
 * Independent physical sensor ledger; prevents double writes from the same sensor
 * through stable batch IDs and WorkManager retry. Only available on devices without
 * native HC on-device recording; never exports an OS-produced duplicate.
 */
class PhysicalStepLedger(context: Context) {
    companion object { private val transactionLock = Any() }
    // Isolate newly observed records and fresh consent; legacy data is read-only.
    private val prefs = context.getSharedPreferences("pikminbee_physical_step_sessions_v2", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("pikminbee_physical_step_sessions", Context.MODE_PRIVATE)

    fun legacyRecordCount(): Int = PhysicalStepIntervalCodec.decode(legacy.getString("sessions", "[]") ?: "[]").size
    fun legacyPendingCount(): Int = PhysicalStepIntervalCodec.decode(legacy.getString("sessions", "[]") ?: "[]").count { !it.synced } +
        if (legacy.getLong("batch_steps", 0) > 0) 1 else 0
    fun lastSyncNote(): String = prefs.getString("last_sync_note", "尚未同步") ?: "尚未同步"
    fun setSyncNote(note: String) { prefs.edit().putString("last_sync_note", note.take(120)).apply() }

    private fun todayKey(now: Long): String =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun optIn() = prefs.getBoolean("sync_sensor_steps_to_hc", false)
    fun setOptIn(value: Boolean) {
        prefs.edit().putBoolean("sync_sensor_steps_to_hc", value).apply()
    }
    @Synchronized fun all(): List<PhysicalStepInterval> =
        PhysicalStepIntervalCodec.decode(prefs.getString("sessions", "[]") ?: "[]")

    @Synchronized fun pending() = all().filterNot { it.synced }

    @Synchronized fun pendingCount(): Int = synchronized(transactionLock) {
        val pending = pending()
        val batchId = prefs.getString("batch_id", null)
        pending.size + if (batchId != null && prefs.getLong("batch_steps", 0) > 0 && all().none { it.id == batchId }) 1 else 0
    }

    @Synchronized fun todaysCount(now: Long = System.currentTimeMillis()): Long =
        if (prefs.getString("day", "") == todayKey(now))
            prefs.getLong("daily_steps", 0L) else 0L

    @Synchronized fun addFromSensor(delta: Long, observedAtMillis: Long, previousAtMillis: Long) {
        synchronized(transactionLock) {
        require(delta in 1..10_000)
        require(observedAtMillis > 0 && previousAtMillis in 1 until observedAtMillis &&
            observedAtMillis - previousAtMillis <= 14_400_000L)
        val currentDay = todayKey(observedAtMillis)
        if (prefs.getString("day", "") != currentDay) {
            if (!flushPending()) throw IllegalStateException("無法保存前一天的感測步數")
        }
        val batchId = prefs.getString("batch_id", null) ?: UUID.randomUUID().toString()
        val oldStart = prefs.getLong("batch_start", 0)
        val start = if (oldStart > 0) oldStart else previousAtMillis
        val latestEnd = observedAtMillis.coerceAtLeast(start + 1)
        val sum = prefs.getLong("batch_steps", 0) + delta
        val storedDaily = todaysCount(observedAtMillis)
        check(prefs.edit()
            .putString("day", currentDay)
            .putLong("daily_steps", storedDaily + delta)
            .putString("batch_id", batchId)
            .putLong("batch_start", start)
            .putLong("batch_end", latestEnd)
            .putLong("batch_steps", sum)
            .commit()) { "無法保存即時感測步數" }
        }
    }

    /** Idempotent across crashes between writing the ledger and clearing a batch. */
    @Synchronized fun flushPending(): Boolean {
        synchronized(transactionLock) {
        val id = prefs.getString("batch_id", null) ?: return true
        val count = prefs.getLong("batch_steps", 0)
        val start = prefs.getLong("batch_start", 0)
        val end = prefs.getLong("batch_end", 0)
        if (count <= 0 || start <= 0 || end <= start) return false
        val before = all()
        if (before.none { it.id == id }) {
            val pending = before.filterNot { it.synced }
            if (pending.size >= 500) return false
            val recentSynced = before.filter { it.synced }.takeLast(499 - pending.size)
            val after = recentSynced + pending + PhysicalStepInterval(id, start, end, count)
            if (!prefs.edit().putString("sessions", PhysicalStepIntervalCodec.encode(after)).commit())
                return false
        }
        return prefs.edit().remove("batch_id").remove("batch_start")
            .remove("batch_end").remove("batch_steps").commit()
        }
    }

    @Synchronized fun markSynced(id: String) {
        synchronized(transactionLock) {
        val before = all()
        if (before.none { it.id == id && !it.synced }) return
        check(prefs.edit().putString("sessions", PhysicalStepIntervalCodec.encode(
            before.map { if (it.id == id) it.copy(synced = true) else it }
        )).commit()) { "無法保存實體步數同步狀態" }
        }
    }

    @Synchronized fun discardPending() {
        synchronized(transactionLock) {
        check(prefs.edit().putString("sessions", PhysicalStepIntervalCodec.encode(
            all().filter { it.synced }
        )).remove("batch_id").remove("batch_start")
            .remove("batch_end").remove("batch_steps").commit()) {
            "無法清除未同步的感測步數"
        }
        }
    }
}
