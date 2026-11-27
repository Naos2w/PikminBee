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
        private const val UNIQUE_NAME = "pikminbee_virtual_estimates_health_connect_v2"

        fun enqueue(context: Context) {
            if (!EstimatedStepLedger.routeSync(context).optIn()) return
            val request = OneTimeWorkRequestBuilder<EstimatedStepsSyncWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                // KEEP can silently strand a newly saved chunk if an older worker
                // finishes after reading its queue. APPEND_OR_REPLACE ensures a
                // subsequent pass, while stable client IDs prevent duplicate writes.
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val ledger = EstimatedStepLedger.routeSync(context)
        if (!ledger.optIn()) return Result.success()
        val sync = HealthConnectStepSync.syncPending(context)
        if (sync.remaining == 0) return Result.success()
        // Missing or revoked consent isn't a transient error; ask the user again in settings.
        if (!ledger.optIn() || !HealthConnectStepSync.hasWritePermission(context)) return Result.success()
        return if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
}
