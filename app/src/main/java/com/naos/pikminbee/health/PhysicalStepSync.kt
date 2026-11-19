package com.naos.pikminbee.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Legacy-device fallback: records actual phone sensor steps only when the user
 * explicitly opts in, AND only on devices without HC-native on-device counting.
 * Never uploads steps the modern Health Connect system already records itself.
 */
object PhysicalStepSync {
    suspend fun syncPending(context: Context): StepSyncResult = withContext(Dispatchers.IO) {
        val ledger = PhysicalStepLedger(context)
        val pending = ledger.pending()
        if (!ledger.optIn()) return@withContext StepSyncResult(0, pending.size,
            "未啟用本機感測器步數同步")
        if (HealthConnectRealSteps.nativeDeviceStepsSupported())
            return@withContext StepSyncResult(0, pending.size,
                "本機已支援 Health Connect 原生計步，避免重複寫入")
        if (!HealthConnectStepSync.installed(context))
            return@withContext StepSyncResult(0, pending.size, "Health Connect 尚不可用")
        if (!HealthConnectStepSync.hasWritePermission(context))
            return@withContext StepSyncResult(0, pending.size, "尚未授權寫入步數")
        val client = HealthConnectClient.getOrCreate(context)
        var written = 0
        for (session in pending.take(50)) {
            val from = Instant.ofEpochMilli(session.startMillis)
            val to = Instant.ofEpochMilli(session.endMillis)
            try {
                client.insertRecords(listOf(StepsRecord(
                    startTime = from,
                    endTime = to,
                    startZoneOffset = ZoneId.systemDefault().rules.getOffset(from),
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(to),
                    count = session.steps,
                    metadata = Metadata.autoRecorded(
                        device = Device(type = Device.TYPE_PHONE),
                        clientRecordId = "pikminbee-physical-" + session.id,
                        clientRecordVersion = 1L,
                    ),
                )))
                ledger.markSynced(session.id)
                written++
            } catch (error: Exception) {
                return@withContext StepSyncResult(written, ledger.pending().size,
                    "實體感測步數同步失敗：" + (error.message ?: "請檢查授權"))
            }
        }
        StepSyncResult(written, ledger.pending().size,
            if (written > 0) "已同步 " + written + " 筆真實手機感測步數"
            else "沒有待同步的感測步數")
    }
}
