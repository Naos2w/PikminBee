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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naos.pikminbee.domain.Coordinate

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
                    .onSuccess { current.value = it }
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
