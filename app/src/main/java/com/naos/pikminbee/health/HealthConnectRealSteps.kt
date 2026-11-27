package com.naos.pikminbee.health

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** System HC capability and aggregate reads, separate from the genuine sensor writer. */
object HealthConnectRealSteps {
    val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val WRITE_STEPS = HealthPermission.getWritePermission(StepsRecord::class)

    /** Native recording takes precedence over the legacy sensor fallback. */
    fun nativeDeviceStepsSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 20

    suspend fun hasPermission(context: Context, permission: String): Boolean = withContext(Dispatchers.IO) {
        if (status(context) != HealthConnectClient.SDK_AVAILABLE) return@withContext false
        try { permission in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    fun status(context: Context): Int = runCatching {
        HealthConnectClient.getSdkStatus(context)
    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    sealed interface Result {
        data class Available(val count: Long) : Result
        data object PermissionRequired : Result
        data object ProviderUnavailable : Result
        data object ReadFailed : Result
    }

    suspend fun todayAggregate(context: Context): Result = withContext(Dispatchers.IO) {
        if (status(context) != HealthConnectClient.SDK_AVAILABLE) return@withContext Result.ProviderUnavailable
        try {
            val client = HealthConnectClient.getOrCreate(context)
            if (READ_STEPS !in client.permissionController.getGrantedPermissions())
                return@withContext Result.PermissionRequired
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val count = client.aggregate(AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, now),
            ))[StepsRecord.COUNT_TOTAL] ?: 0L
            // Recheck after reading; never show a value if access was revoked in flight.
            if (READ_STEPS !in client.permissionController.getGrantedPermissions())
                Result.PermissionRequired
            else if (count < 0L || zone != ZoneId.systemDefault() ||
                now.atZone(zone).toLocalDate() != LocalDate.now(zone)) Result.ReadFailed
            else Result.Available(count)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            Result.PermissionRequired
        } catch (_: Exception) {
            Result.ReadFailed
        }
    }
}
