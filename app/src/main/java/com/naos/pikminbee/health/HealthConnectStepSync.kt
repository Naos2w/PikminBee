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
                    it.metadata.clientRecordId?.startsWith("pikminbee-virtual-") == true
                }.sumOf { it.count }
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

    suspend fun syncPending(context: Context): StepSyncResult = withContext(Dispatchers.IO) {
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
        var written = 0
        for (session in ledger.pending().take(50)) {
            val start = Instant.ofEpochMilli(session.startMillis)
            val end = Instant.ofEpochMilli(session.endMillis)
            if (!end.isAfter(start)) continue
            val offset = ZoneId.systemDefault().rules.getOffset(start)
            val record = StepsRecord(
                count = session.steps,
                startTime = start,
                endTime = end,
                startZoneOffset = offset,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
                metadata = Metadata.manualEntry(
                    clientRecordId = "pikminbee-virtual-" + session.id,
                    clientRecordVersion = 1L,
                ),
            )
            try {
                client.insertRecords(listOf(record))
                // Keep the same stable ID on retry if the local ack write fails.
                ledger.markSynced(session.id)
                written++
            } catch (error: Exception) {
                return@withContext publish(StepSyncResult(written, ledger.pending().size,
                    "Health Connect 寫入失敗：" + (error.message ?: "請檢查權限")))
            }
        }
        publish(StepSyncResult(written, ledger.pending().size,
            if (written > 0) "已同步 " + written + " 筆手動估算步數紀錄"
            else "目前沒有待同步的模擬步數紀錄"))
    }
}
