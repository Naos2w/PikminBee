package com.naos.pikminbee.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class StepSyncResult(val written: Int, val remaining: Int, val note: String)

/** Route estimates are explicitly opted-in manual entries, not sensor readings.
 * Restore the 0.28.1 writer and stable IDs against the new consent/queue only.
 */
object HealthConnectStepSync {
    private val syncMutex = Mutex()
    val lastSyncResult = MutableStateFlow<StepSyncResult?>(null)

    suspend fun hasWritePermission(context: Context): Boolean =
        HealthConnectRealSteps.hasPermission(context, HealthConnectRealSteps.WRITE_STEPS)

    suspend fun discardPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val ledger = EstimatedStepLedger.routeSync(context)
            if (ledger.optIn() && hasWritePermission(context)) return@withLock false
            runCatching { ledger.discardPending() }.isSuccess
        }
    }

    suspend fun syncPending(context: Context): StepSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val ledger = EstimatedStepLedger.routeSync(context)
            fun publish(written: Int, note: String): StepSyncResult =
                StepSyncResult(written, ledger.pending().size, note).also {
                    lastSyncResult.value = it
                }
            if (!ledger.optIn()) return@withLock publish(0, "未啟用路線估算步數同步")
            if (!hasWritePermission(context))
                return@withLock publish(0, "尚未授權或已撤銷 Health Connect 寫入")
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val outcome = VirtualStepSyncIntegrity.drainAll(
                    sessions = ledger.pending(),
                    optedIn = { ledger.optIn() },
                    stillPending = { id -> ledger.pending().any { it.id == id } },
                    insert = { session ->
                        // A shared Android permission is not consent for this source.
                        if (!ledger.optIn() || !hasWritePermission(context))
                            throw SecurityException("同步已關閉或寫入權限已撤銷")
                        val start = Instant.ofEpochMilli(session.startMillis)
                        val end = Instant.ofEpochMilli(session.endMillis)
                        require(start.isBefore(end) && !end.isAfter(Instant.now())) {
                            "估算紀錄時間無效；保留於本機"
                        }
                        val zone = ZoneId.systemDefault()
                        client.insertRecords(listOf(StepsRecord(
                            count = session.steps,
                            startTime = start, endTime = end,
                            startZoneOffset = zone.rules.getOffset(start),
                            endZoneOffset = zone.rules.getOffset(end),
                            metadata = Metadata.manualEntry(
                                clientRecordId = VirtualStepSyncIntegrity.clientRecordId(session.id),
                                clientRecordVersion = 1L,
                            ),
                        )))
                    },
                    acknowledge = { id -> ledger.markSynced(id) },
                )
                val note = when {
                    outcome.optedOut -> "已關閉路線估算同步；資料保留"
                    outcome.failure != null -> "Health Connect 同步未完成：${outcome.failure}"
                    outcome.acknowledged > 0 -> "已同步 ${outcome.acknowledged} 筆手動估算紀錄"
                    else -> "無待同步路線估算紀錄"
                }
                publish(outcome.acknowledged, note)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                publish(0, "路線估算同步未完成；資料保留，可稍後重試")
            }
        }
    }
}
