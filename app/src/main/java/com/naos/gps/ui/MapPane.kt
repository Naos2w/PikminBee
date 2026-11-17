package com.naos.gps.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naos.gps.BuildConfig
import com.naos.gps.data.MapProvider
import com.naos.gps.domain.Coordinate
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GoogleMapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.MarkerOptions as GoogleMarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView as LibreMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition as LibreCameraPosition
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.annotations.MarkerOptions as LibreMarkerOptions

private const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** Shared selection and coordinates across independent native map renderers. */
@Composable
fun LocationMap(
    provider: MapProvider,
    dark: Boolean,
    selected: Coordinate,
    onPick: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (provider) {
        MapProvider.OPEN_FREE_MAP -> OpenFreeMapPane(dark, selected, onPick, modifier)
        MapProvider.GOOGLE_MAPS -> if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
            GoogleMapsPane(dark, selected, onPick, modifier)
        } else {
            Box(modifier) {
                Text("Google Maps 尚未設定 API Key，請先在 secrets.properties 填寫設定。")
            }
        }
    }
}

@Composable
private fun OpenFreeMapPane(
    dark: Boolean,
    selected: Coordinate,
    onPick: (Coordinate) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        LibreMapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var marker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isAttributionEnabled = true
            ready.addOnMapClickListener { point ->
                onPick(Coordinate(point.latitude, point.longitude))
                true
            }
            ready.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                LibreCameraPosition.Builder()
                    .target(LibreLatLng(selected.latitude, selected.longitude))
                    .zoom(14.5).build()
            ))
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, dark) {
        map?.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIBERTY)) {
            marker = null
        }
    }
    LaunchedEffect(map, selected) {
        map?.let { m ->
            marker?.let { m.removeMarker(it) }
            marker = m.addMarker(LibreMarkerOptions().position(
                LibreLatLng(selected.latitude, selected.longitude)).title("選取位置"))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun GoogleMapsPane(
    dark: Boolean,
    selected: Coordinate,
    onPick: (Coordinate) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) {
        GoogleMapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var marker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    DisposableEffect(view, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isMapToolbarEnabled = false
            ready.setOnMapClickListener { point ->
                onPick(Coordinate(point.latitude, point.longitude))
            }
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(
                GoogleLatLng(selected.latitude, selected.longitude), 14.5f))
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map = null
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, dark) {
        map?.setMapStyle(if (dark) MapStyleOptions(GOOGLE_DARK_STYLE) else null)
    }
    LaunchedEffect(map, selected) {
        map?.let { m ->
            marker?.remove()
            marker = m.addMarker(GoogleMarkerOptions().position(
                GoogleLatLng(selected.latitude, selected.longitude)).title("選取位置"))
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

// Original dark scheme used only with official Google Maps SDK; no tile scraping.
private const val GOOGLE_DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1b2433"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#afbed0"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#152031"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#35435a"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0c2840"}]},
 {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#263447"}]}
]"""
