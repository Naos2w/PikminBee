package com.naos.pikminbee.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Durable explicit-consent legacy-step export, never competing with HC native capture. */
class PhysicalStepsSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    companion object {
        private const val NAME = "pikminbee_physical_steps_health_connect"

        fun enqueue(context: Context) {
            if (!PhysicalStepLedger(context).optIn()) return
            if (HealthConnectRealSteps.nativeDeviceStepsSupported()) return
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PhysicalStepsSyncWorker>().build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val repo = PhysicalStepLedger(applicationContext)
        if (!repo.optIn() || HealthConnectRealSteps.nativeDeviceStepsSupported())
            return Result.success()
        val outcome = PhysicalStepSync.syncPending(applicationContext)
        if (outcome.remaining == 0 || !HealthConnectStepSync.hasWritePermission(applicationContext))
            return Result.success()
        return if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
}
