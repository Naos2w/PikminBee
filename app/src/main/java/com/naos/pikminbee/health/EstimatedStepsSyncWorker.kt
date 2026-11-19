package com.naos.pikminbee.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager

/** Durable, opt-in export of locally queued *virtual* step estimates only.
 * WorkManager survives foreground-service shutdown; retries retain stable client IDs.
 */
class EstimatedStepsSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val UNIQUE_NAME = "pikminbee_virtual_estimates_health_connect"

        fun enqueue(context: Context) {
            if (!EstimatedStepLedger(context).optIn()) return
            val request = OneTimeWorkRequestBuilder<EstimatedStepsSyncWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val ledger = EstimatedStepLedger(context)
        if (!ledger.optIn()) return Result.success()
        val sync = HealthConnectStepSync.syncPending(context)
        if (sync.remaining == 0) return Result.success()
        // Missing or revoked consent isn't a transient error; ask the user again in settings.
        if (!HealthConnectStepSync.hasWritePermission(context)) return Result.success()
        return if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
}
