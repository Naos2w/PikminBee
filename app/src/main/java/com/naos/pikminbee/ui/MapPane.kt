package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GoogleMapView
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.MarkerOptions as GoogleMarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions as GooglePolylineOptions
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.domain.Coordinate
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView as LibreMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition as LibreCameraPosition
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.annotations.MarkerOptions as LibreMarkerOptions
import org.maplibre.android.annotations.PolylineOptions as LibrePolylineOptions

private const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

/** Both native providers consume precisely the same picked stops and real road geometry. */
@Composable
fun LocationMap(
    provider: MapProvider,
    dark: Boolean,
    selected: Coordinate,
    stops: List<Coordinate>,
    road: List<Coordinate>,
    onPick: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (provider) {
        MapProvider.OPEN_FREE_MAP -> OpenFreeMapPane(dark, selected, stops, road, onPick, modifier)
        MapProvider.GOOGLE_MAPS -> if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
            GoogleMapsPane(dark, selected, stops, road, onPick, modifier)
        } else {
            Box(modifier) { Text("Google Maps 尚未設定 API Key，請先在 secrets.properties 填寫設定。") }
        }
    }
}

@Composable
private fun OpenFreeMapPane(
    dark: Boolean, selected: Coordinate, stops: List<Coordinate>,
    road: List<Coordinate>, onPick: (Coordinate) -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPick by rememberUpdatedState(onPick)
    val view = remember(context) {
        MapLibre.getInstance(context)
        LibreMapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var currentMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var currentStyleVersion by remember { mutableIntStateOf(-1) }
    var stopMarkers by remember { mutableStateOf(emptyList<org.maplibre.android.annotations.Marker>()) }
    var line by remember { mutableStateOf<org.maplibre.android.annotations.Polyline?>(null) }
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
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isAttributionEnabled = true
            ready.addOnMapClickListener { point ->
                latestOnPick(Coordinate(point.latitude, point.longitude))
                true
            }
            ready.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                LibreCameraPosition.Builder()
                    .target(LibreLatLng(selected.latitude, selected.longitude)).zoom(14.5).build()
            ))
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
        map?.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIBERTY)) {
            styleVersion++
        }
    }
    // The moving position is updated in place. Do not recreate the whole route every second.
    LaunchedEffect(map, styleVersion, selected) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        val position = LibreLatLng(selected.latitude, selected.longitude)
        if (currentMarker == null || currentStyleVersion != styleVersion) {
            currentMarker?.let { m.removeMarker(it) }
            currentMarker = m.addMarker(LibreMarkerOptions()
                .position(position).title("選取位置"))
            currentStyleVersion = styleVersion
        } else currentMarker?.position = position
    }
    LaunchedEffect(map, styleVersion, stops, road) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        stopMarkers.forEach { m.removeMarker(it) }
        line?.let { m.removePolyline(it) }
        stopMarkers = stops.mapIndexed { index, coordinate ->
            m.addMarker(LibreMarkerOptions()
                .position(LibreLatLng(coordinate.latitude, coordinate.longitude))
                .title("路點 " + (index + 1)))
        }
        line = if (road.size >= 2) m.addPolyline(LibrePolylineOptions()
            .addAll(road.map { LibreLatLng(it.latitude, it.longitude) })
            .color(android.graphics.Color.rgb(232, 151, 39)).width(5f)) else null
    }
    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
private fun GoogleMapsPane(
    dark: Boolean, selected: Coordinate, stops: List<Coordinate>,
    road: List<Coordinate>, onPick: (Coordinate) -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPick by rememberUpdatedState(onPick)
    val view = remember(context) { GoogleMapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var currentMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var stopMarkers by remember { mutableStateOf(emptyList<com.google.android.gms.maps.model.Marker>()) }
    var line by remember { mutableStateOf<com.google.android.gms.maps.model.Polyline?>(null) }
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
                latestOnPick(Coordinate(point.latitude, point.longitude))
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
        val m = map ?: return@LaunchedEffect
        val position = GoogleLatLng(selected.latitude, selected.longitude)
        if (currentMarker == null) {
            currentMarker = m.addMarker(GoogleMarkerOptions().position(position).title("選取位置"))
        } else currentMarker?.position = position
    }
    LaunchedEffect(map, stops, road) {
        val m = map ?: return@LaunchedEffect
        stopMarkers.forEach { it.remove() }
        line?.remove()
        stopMarkers = stops.mapIndexedNotNull { index, coordinate ->
            m.addMarker(GoogleMarkerOptions()
                .position(GoogleLatLng(coordinate.latitude, coordinate.longitude))
                .title("路點 " + (index + 1)))
        }
        line = if (road.size >= 2) m.addPolyline(GooglePolylineOptions()
            .addAll(road.map { GoogleLatLng(it.latitude, it.longitude) })
            .color(android.graphics.Color.rgb(232, 151, 39)).width(6f)) else null
    }
    AndroidView(factory = { view }, modifier = modifier)
}

private const val GOOGLE_DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1b2433"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#afbed0"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#152031"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#35435a"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0c2840"}]},
 {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#263447"}]}
]"""
