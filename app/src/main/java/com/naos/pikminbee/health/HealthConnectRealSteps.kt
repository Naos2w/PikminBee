package com.naos.pikminbee.health

import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android 14 + extension 20 provides on-device step recording via Health Connect.
 * Granting READ_STEPS enables native step capture; do not write a second copy.
 * Aggregates are priority-deduplicated across all sources, not guaranteed sensor-only.
 */
object HealthConnectRealSteps {
    val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)

    fun nativeDeviceStepsSupported(
        sdkInt: Int = Build.VERSION.SDK_INT,
        // Guard the API itself using the actual runtime SDK, not the test-injectable sdkInt.
        // SdkExtensions.getExtensionVersion was introduced in Android 11 (API 30).
        extension: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) else 0,
    ): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && extension >= 20

    suspend fun hasReadPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!HealthConnectStepSync.installed(context)) return@withContext false
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController
                .getGrantedPermissions().contains(READ_STEPS)
        }.getOrDefault(false)
    }

    suspend fun todayAggregate(context: Context): Long? = withContext(Dispatchers.IO) {
        if (!hasReadPermission(context)) return@withContext null
        runCatching {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val result = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                )
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        }.getOrNull()
    }
}
