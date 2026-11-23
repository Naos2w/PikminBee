package com.naos.pikminbee.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StepSyncResult(val written: Int, val remaining: Int, val note: String)

/**
 * Export only the user's explicitly opted-in virtual step estimates.
 * Records have stable client IDs and MANUAL_ENTRY metadata, never AUTO_RECORDED.
 * No HC write occurs without granted WRITE_STEPS and opt-in.
 */
object HealthConnectStepSync {
    val WRITE_STEPS: String = HealthPermission.getWritePermission(StepsRecord::class)
    /** Serialise manual sync and WorkManager in the same app process. */
    private val syncMutex = Mutex()
    /** Separately observable result; a grant is not evidence of a successful write. */
    val lastSyncResult = MutableStateFlow<StepSyncResult?>(null)

    private fun publish(result: StepSyncResult): StepSyncResult {
        lastSyncResult.value = result
        return result
    }

    /** Count this app's actual manual virtual records, ignoring aggregate priority. */
    suspend fun todayOwnVirtualSteps(context: Context): Long? = withContext(Dispatchers.IO) {
        if (!installed(context)) return@withContext null
        runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().contains(
                HealthPermission.getReadPermission(StepsRecord::class))) return@runCatching null
            val zone = ZoneId.systemDefault()
            val from = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val to = Instant.now()
            var pageToken: String? = null
            var sum = 0L
            do {
                val response = client.readRecords(ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = setOf(DataOrigin(context.packageName)),
                    pageSize = 100,
                    pageToken = pageToken,
                ))
                sum += response.records.filter {
                    it.metadata.clientRecordId?.startsWith(
                        VirtualStepSyncIntegrity.CLIENT_ID_PREFIX) == true
                }.sumOf { record ->
                    VirtualStepSyncIntegrity.estimatedStepsInWindow(
                        record.count,
                        record.startTime.toEpochMilli(), record.endTime.toEpochMilli(),
                        from.toEpochMilli(), to.toEpochMilli(),
                    )
                }
                pageToken = response.pageToken
            } while (pageToken != null)
            sum
        }.getOrNull()
    }

    fun installed(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasWritePermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!installed(context)) return@withContext false
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController
                .getGrantedPermissions().contains(WRITE_STEPS)
        }.getOrDefault(false)
    }

    /** Foreground and worker calls share one writer; only acknowledged items leave the queue. */
    suspend fun syncPending(context: Context): StepSyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val ledger = EstimatedStepLedger(context)
            if (!ledger.optIn())
                return@withContext publish(StepSyncResult(0, ledger.pending().size,
                    "未啟用虛擬估算步數同步"))
            if (!installed(context))
                return@withContext publish(StepSyncResult(0, ledger.pending().size,
                    "此裝置尚未提供 Health Connect"))
            val client = HealthConnectClient.getOrCreate(context)
            val authorized = runCatching {
                client.permissionController.getGrantedPermissions().contains(WRITE_STEPS)
            }.getOrDefault(false)
            if (!authorized)
                return@withContext publish(StepSyncResult(0, ledger.pending().size,
                    "尚未授權 Health Connect 寫入步數"))

            val outcome = VirtualStepSyncIntegrity.drainAll(
                sessions = ledger.pending(),
                optedIn = { ledger.optIn() },
                stillPending = { id -> ledger.pending().any { it.id == id } },
                insert = { session ->
                    val start = Instant.ofEpochMilli(session.startMillis)
                    val end = Instant.ofEpochMilli(session.endMillis)
                    val zone = ZoneId.systemDefault()
                    val record = StepsRecord(
                        count = session.steps,
                        startTime = start,
                        endTime = end,
                        startZoneOffset = zone.rules.getOffset(start),
                        endZoneOffset = zone.rules.getOffset(end),
                        // Retry after an ack failure must use EXACTLY the same ID.
                        // A virtual estimate is always a user-authorised manual entry.
                        metadata = Metadata.manualEntry(
                            clientRecordId =
                                VirtualStepSyncIntegrity.clientRecordId(session.id),
                            clientRecordVersion = 1L,
                        ),
                    )
                    client.insertRecords(listOf(record))
                },
                acknowledge = { id -> ledger.markSynced(id) },
            )
            val remaining = ledger.pending().size
            val note = when {
                outcome.optedOut -> "使用者已關閉同步，剩餘虛擬估算未寫入"
                outcome.failure != null -> "Health Connect 寫入失敗：" + outcome.failure
                outcome.acknowledged > 0 ->
                    "已同步 " + outcome.acknowledged + " 筆手動估算步數紀錄"
                else -> "目前沒有待同步的模擬步數紀錄"
            }
            publish(StepSyncResult(outcome.acknowledged, remaining, note))
        }
    }
}
