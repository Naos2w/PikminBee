package com.naos.pikminbee.health

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Only *virtual* route estimates; recording them never implies physical exercise. */
data class EstimatedStepSession(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val steps: Long,
    val synced: Boolean = false,
) {
    init {
        require(id.isNotBlank() && id.length <= 100)
        require(startMillis > 0L && endMillis >= startMillis)
        require(steps in 1L..10_000_000L)
    }
}

object EstimatedStepSessionCodec {
    fun encode(sessions: List<EstimatedStepSession>): String = JSONArray().apply {
        sessions.forEach { session ->
            put(JSONObject().put("id", session.id).put("start", session.startMillis)
                .put("end", session.endMillis).put("steps", session.steps)
                .put("synced", session.synced))
        }
    }.toString()

    fun decode(json: String): List<EstimatedStepSession> = runCatching {
        val array = JSONArray(json)
        require(array.length() <= 500)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.getJSONObject(index)
            runCatching {
                EstimatedStepSession(
                    entry.getString("id"),
                    entry.getLong("start"),
                    entry.getLong("end"),
                    entry.getLong("steps"),
                    entry.optBoolean("synced", false),
                )
            }.getOrNull()
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

class EstimatedStepLedger(context: Context) {
    companion object { private val transactionLock = Any() }
    private val prefs = context.getSharedPreferences("pikminbee_virtual_step_sessions", Context.MODE_PRIVATE)

    @Synchronized fun pending(): List<EstimatedStepSession> = all().filterNot { it.synced }

    @Synchronized fun all(): List<EstimatedStepSession> =
        EstimatedStepSessionCodec.decode(prefs.getString("sessions", "[]") ?: "[]")

    @Synchronized fun add(startMillis: Long, endMillis: Long, stepCount: Long):
        EstimatedStepSession? {
        if (stepCount <= 0L || endMillis <= startMillis) return null
        return synchronized(transactionLock) {
        val next = EstimatedStepSession(
            UUID.randomUUID().toString(), startMillis, endMillis, stepCount)
        val previous = all()
        val unsynced = previous.filterNot { it.synced }
        check(unsynced.size < 500) { "待同步紀錄已達 500 筆，請先同步或整理資料" }
        val recentSynced = previous.filter { it.synced }.takeLast(499 - unsynced.size)
        check(prefs.edit().putString("sessions", EstimatedStepSessionCodec.encode(recentSynced + unsynced + next))
            .commit()) { "無法保存虛擬步數紀錄" }
        next
        }
    }

    /** User can discard unexported virtual estimates without altering HC records. */
    @Synchronized fun discardPending() {
        synchronized(transactionLock) {
        check(prefs.edit().putString("sessions", EstimatedStepSessionCodec.encode(
            all().filter { it.synced }
        )).commit()) { "無法清除尚未同步的虛擬步數" }
        }
    }

    @Synchronized fun markSynced(id: String) {
        synchronized(transactionLock) {
        val before = all()
        if (before.none { it.id == id && !it.synced }) return
        check(prefs.edit().putString("sessions", EstimatedStepSessionCodec.encode(
            before.map { if (it.id == id) it.copy(synced = true) else it }
        )).commit()) { "無法保存步數同步狀態" }
        }
    }

    fun optIn(): Boolean = prefs.getBoolean("sync_virtual_estimates_to_hc", false)

    fun setOptIn(value: Boolean) {
        // Never enable without the user explicitly selecting this setting.
        prefs.edit().putBoolean("sync_virtual_estimates_to_hc", value).apply()
    }
}
