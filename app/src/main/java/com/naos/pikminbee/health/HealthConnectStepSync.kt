package com.naos.pikminbee.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class StepSyncResult(val written: Int, val remaining: Int, val note: String)

/**
 * Export only the user's explicitly opted-in virtual step estimates.
 * Records have stable client IDs and MANUAL_ENTRY metadata, never AUTO_RECORDED.
 * No HC write occurs without granted WRITE_STEPS and opt-in.
 */
object HealthConnectStepSync {
    val WRITE_STEPS: String = HealthPermission.getWritePermission(StepsRecord::class)

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
            return@withContext StepSyncResult(0, ledger.pending().size,
                "未啟用虛擬估算步數同步")
        if (!installed(context))
            return@withContext StepSyncResult(0, ledger.pending().size,
                "此裝置尚未提供 Health Connect")
        val client = HealthConnectClient.getOrCreate(context)
        val authorized = runCatching {
            client.permissionController.getGrantedPermissions().contains(WRITE_STEPS)
        }.getOrDefault(false)
        if (!authorized)
            return@withContext StepSyncResult(0, ledger.pending().size,
                "尚未授權 Health Connect 寫入步數")
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
                return@withContext StepSyncResult(written, ledger.pending().size,
                    "Health Connect 寫入失敗：" + (error.message ?: "請檢查權限"))
            }
        }
        StepSyncResult(written, ledger.pending().size,
            if (written > 0) "已同步 " + written + " 筆手動估算步數紀錄"
            else "目前沒有待同步的模擬步數紀錄")
    }
}
