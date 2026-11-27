package com.naos.pikminbee.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Only a fresh opt-in can enqueue real-sensor records from the new private ledger. */
class RealSensorSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    companion object {
        private const val NAME = "pikminbee_real_sensor_hc_v2"
        fun enqueue(context: Context) {
            if (!PhysicalStepLedger(context).optIn() || HealthConnectRealSteps.nativeDeviceStepsSupported()) return
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RealSensorSyncWorker>().build())
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!PhysicalStepLedger(applicationContext).optIn() || HealthConnectRealSteps.nativeDeviceStepsSupported())
            return Result.success()
        val outcome = PhysicalStepSync.syncPending(applicationContext)
        return if (outcome.retryable && runAttemptCount < 4) Result.retry()
        else if (outcome.retryable) Result.failure() else Result.success()
    }
}
