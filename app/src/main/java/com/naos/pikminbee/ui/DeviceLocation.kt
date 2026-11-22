package com.naos.pikminbee.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.naos.pikminbee.location.LocationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naos.pikminbee.domain.Coordinate

/** Verified physical GPS reference, memory-only. Mock samples never enter this flow. */
data class VerifiedDeviceFix(
    val coordinate: Coordinate, val timestampMillis: Long,
    val accuracyMetres: Float? = null,
)

object VerifiedPhysicalFixState {
    val latest = MutableStateFlow<VerifiedDeviceFix?>(null)
    fun fresh(nowMillis: Long = System.currentTimeMillis()): VerifiedDeviceFix? =
        latest.value?.takeIf { nowMillis - it.timestampMillis in 0..(30 * 60_000L) }
}

/**
 * Foreground-only phone location for the map's blue dot.
 * Never changes the system location or starts a mock location provider.
 * Android can itself report the mock location while mock GPS is enabled;
 * the map therefore uses a bee marker instead of showing a misleading real fix.
 */
@Composable
fun rememberDeviceLocation(refresh: Int = 0): State<Coordinate?> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val current = remember { mutableStateOf<Coordinate?>(null) }
    DisposableEffect(context, lifecycle, refresh) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { Coordinate(location.latitude, location.longitude) }
                    .onSuccess { point ->
                        current.value = point
                        // A recent, explicitly non-mock Android sample is needed
                        // before the favourites clock may compare time zones.
                        if (!LocationCompat.isMock(location) &&
                            !LocationStatus.mockActive.value &&
                            System.currentTimeMillis() - location.time in 0..120_000L) {
                            VerifiedPhysicalFixState.latest.value =
                                VerifiedDeviceFix(point, location.time,
                                    location.accuracy.takeIf {
                                        location.hasAccuracy() && it.isFinite() && it >= 0f
                                    })
                        }
                    }
            }
        }
        fun stop() { runCatching { manager.removeUpdates(listener) } }
        fun start() {
            stop()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                current.value = null
                return
            }
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val available = providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            available.forEach { provider ->
                runCatching {
                    manager.getLastKnownLocation(provider)
                }.getOrNull()?.takeIf { it.time > System.currentTimeMillis() - 5 * 60_000 }?.let {
                    listener.onLocationChanged(it)
                }
                runCatching { manager.requestLocationUpdates(provider, 4_000L, 3f, listener, Looper.getMainLooper()) }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            stop()
            lifecycle.lifecycle.removeObserver(observer)
        }
    }
    return current
}
