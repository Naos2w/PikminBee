package com.naos.pikminbee.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Genuine hardware records only. No dependency on route or simulated-distance state. */
object PhysicalStepSync {
    data class Outcome(val written: Int, val remaining: Int, val note: String, val retryable: Boolean = false)
    private val mutex = Mutex()

    /** Destructive local cleanup is explicit, stopped, and serialized with writes. */
    suspend fun discardPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ledger = PhysicalStepLedger(context)
            if (ledger.optIn() || PhysicalStepsStatus.state.value.tracking) return@withLock false
            runCatching { ledger.discardPending() }.isSuccess
        }
    }

    suspend fun syncPending(context: Context): Outcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ledger = PhysicalStepLedger(context)
            var written = 0
            fun finish(note: String, retryable: Boolean = false): Outcome {
                ledger.setSyncNote(note)
                return Outcome(written, ledger.pendingCount(), note, retryable)
            }
            if (!ledger.optIn()) return@withLock finish("未啟用真實感測步數同步")
            if (HealthConnectRealSteps.nativeDeviceStepsSupported())
                return@withLock finish("由 Health Connect 原生計步，不重複寫入")
            if (!HealthConnectRealSteps.hasPermission(context, HealthConnectRealSteps.WRITE_STEPS))
                return@withLock finish("尚未授權或已撤銷步數寫入")
            try {
                if (!ledger.flushPending()) return@withLock finish("待同步感測紀錄尚未能保存；保留本機資料")
                val client = HealthConnectClient.getOrCreate(context)
                for (record in ledger.pending().take(50)) {
                    // Recheck every record. Old virtual/physical stores are never read here.
                    if (!ledger.optIn() || HealthConnectRealSteps.nativeDeviceStepsSupported() ||
                        !HealthConnectRealSteps.hasPermission(context, HealthConnectRealSteps.WRITE_STEPS))
                        return@withLock finish("已停止同步或授權已變更")
                    val from = Instant.ofEpochMilli(record.startMillis)
                    val to = Instant.ofEpochMilli(record.endMillis)
                    if (to.isAfter(Instant.now()) || !from.isBefore(to))
                        return@withLock finish("感測紀錄時間無效；保留待人工整理")
                    val zone = ZoneId.systemDefault()
                    client.insertRecords(listOf(StepsRecord(
                        startTime = from, endTime = to,
                        startZoneOffset = zone.rules.getOffset(from),
                        endZoneOffset = zone.rules.getOffset(to),
                        count = record.steps,
                        metadata = Metadata.autoRecorded(
                            device = Device(type = Device.TYPE_PHONE),
                            clientRecordId = "pikminbee-real-sensor-v2-" + record.id,
                            clientRecordVersion = 1L,
                        ),
                    )))
                    ledger.markSynced(record.id)
                    written++
                }
                finish(if (written > 0) "已同步 $written 筆實際手機感測紀錄" else "沒有待同步的感測紀錄",
                    ledger.pending().isNotEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                finish("寫入權限已撤銷；未同步資料留在手機")
            } catch (_: Exception) {
                finish("感測紀錄同步未完成；資料保留，可稍後重試", true)
            }
        }
    }
}
