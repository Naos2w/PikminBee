package com.naos.pikminbee.health

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

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
        require(startMillis > 0L && endMillis > startMillis)
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
            // One malformed historical record must not erase the other 499
            // queued estimates. Decode each entry inside its own failure guard.
            runCatching {
                val entry = array.getJSONObject(index)
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

class EstimatedStepLedger(context: Context, private val activeRouteSync: Boolean = false) {
    companion object {
        private val transactionLock = Any()
        /** Fresh opt-in and queue; the 0.28.1 ledger remains available for history only. */
        fun routeSync(context: Context) = EstimatedStepLedger(context, activeRouteSync = true)
        /** Signals UI readers when the queue changes, including after background sync. */
        val changes = MutableStateFlow(0L)
        private fun changed() { changes.value += 1L }
    }
    private val prefs = context.getSharedPreferences(
        if (activeRouteSync) "pikminbee_virtual_step_sessions_v2" else "pikminbee_virtual_step_sessions",
        Context.MODE_PRIVATE,
    )

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
        changed()
        next
        }
    }

    /** User can discard unexported virtual estimates without altering HC records. */
    @Synchronized fun discardPending() {
        synchronized(transactionLock) {
        check(prefs.edit().putString("sessions", EstimatedStepSessionCodec.encode(
            all().filter { it.synced }
        )).commit()) { "無法清除尚未同步的虛擬步數" }
        changed()
        }
    }

    @Synchronized fun markSynced(id: String) {
        synchronized(transactionLock) {
        val before = all()
        if (before.none { it.id == id && !it.synced }) return
        check(prefs.edit().putString("sessions", EstimatedStepSessionCodec.encode(
            before.map { if (it.id == id) it.copy(synced = true) else it }
        )).commit()) { "無法保存步數同步狀態" }
        changed()
        }
    }

    fun optIn(): Boolean = activeRouteSync && prefs.getBoolean("sync_virtual_estimates_to_hc", false)

    fun setOptIn(value: Boolean) {
        check(activeRouteSync) { "Legacy estimates cannot be opted into export" }
        // Never reuse consent from the old ledger.
        prefs.edit().putBoolean("sync_virtual_estimates_to_hc", value).apply()
    }
}
