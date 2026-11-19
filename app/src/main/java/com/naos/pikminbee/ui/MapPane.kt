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
import com.google.android.gms.maps.model.LatLngBounds as GoogleLatLngBounds
import com.naos.pikminbee.domain.MapCameraGeometry
import com.naos.pikminbee.BuildConfig
import com.naos.pikminbee.data.MapProvider
import com.naos.pikminbee.data.FreeMapStyle
import com.naos.pikminbee.data.MapBasemapPolicy
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.flower.FlowerSpot
import com.naos.pikminbee.flower.FlowerEvidence
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView as LibreMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition as LibreCameraPosition
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.annotations.IconFactory as LibreIconFactory
import org.maplibre.android.annotations.MarkerOptions as LibreMarkerOptions
import org.maplibre.android.annotations.PolylineOptions as LibrePolylineOptions
import org.maplibre.android.geometry.LatLngBounds as LibreLatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory as LibreCameraUpdateFactory

/** An explicit, one-shot camera instruction. Live GPS updates never move the camera. */
data class MapCameraRequest(
    val id: Int,
    val target: MapCameraGeometry.ViewTarget,
)

/** Both native providers consume precisely the same picked stops and real road geometry. */
@Composable
fun LocationMap(
    provider: MapProvider,
    dark: Boolean,
    selected: Coordinate,
    current: Coordinate?,
    currentIsMock: Boolean,
    start: Coordinate?,
    stops: List<Coordinate>,
    road: List<Coordinate>,
    previewRoad: Boolean,
    flowerSpots: List<FlowerSpot>,
    freeMapStyle: FreeMapStyle = FreeMapStyle.AUTO,
    cameraRequest: MapCameraRequest? = null,
    mapExplore: Boolean = false,
    onCameraFailure: (String) -> Unit = {},
    onPick: (Coordinate) -> Unit,
    onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (provider) {
        MapProvider.OPEN_FREE_MAP -> OpenFreeMapPane(dark, selected, current, currentIsMock, start, stops, road, previewRoad, flowerSpots,
            freeMapStyle, cameraRequest, mapExplore, onCameraFailure,
            onPick, onFlowerPick, onStopPick, modifier)
        MapProvider.GOOGLE_MAPS -> if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
            GoogleMapsPane(dark, selected, current, currentIsMock, start, stops, road, previewRoad, flowerSpots,
                cameraRequest, mapExplore, onCameraFailure, onPick, onFlowerPick, onStopPick, modifier)
        } else {
            OpenFreeMapPane(dark, selected, current, currentIsMock, start, stops, road, previewRoad, flowerSpots,
                freeMapStyle, cameraRequest, mapExplore, onCameraFailure,
                onPick, onFlowerPick, onStopPick, modifier)
        }
    }
}

@Composable
private fun OpenFreeMapPane(
    dark: Boolean, selected: Coordinate, current: Coordinate?, currentIsMock: Boolean,
    start: Coordinate?, stops: List<Coordinate>,
    road: List<Coordinate>, previewRoad: Boolean, flowerSpots: List<FlowerSpot>,
    freeMapStyle: FreeMapStyle, cameraRequest: MapCameraRequest?, mapExplore: Boolean,
    onCameraFailure: (String) -> Unit,
    onPick: (Coordinate) -> Unit, onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)?, modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPick by rememberUpdatedState(onPick)
    val latestOnFlowerPick by rememberUpdatedState(onFlowerPick)
    val latestOnStopPick by rememberUpdatedState(onStopPick)
    val latestStops by rememberUpdatedState(stops)
    val latestOnCameraFailure by rememberUpdatedState(onCameraFailure)
    val view = remember(context) {
        MapLibre.getInstance(context)
        LibreMapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var currentMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var currentStyleVersion by remember { mutableIntStateOf(-1) }
    var liveMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var liveStyleVersion by remember { mutableIntStateOf(-1) }
    var liveWasMock by remember { mutableStateOf(false) }
    var startMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var startStyleVersion by remember { mutableIntStateOf(-1) }
    var stopMarkers by remember { mutableStateOf(emptyList<org.maplibre.android.annotations.Marker>()) }
    var flowerMarkers by remember { mutableStateOf(emptyList<org.maplibre.android.annotations.Marker>()) }
    var shownFlowers by remember { mutableStateOf(emptyList<FlowerSpot>()) }
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
            ready.uiSettings.isScrollGesturesEnabled = true
            ready.uiSettings.isZoomGesturesEnabled = true
            ready.uiSettings.isRotateGesturesEnabled = true
            ready.uiSettings.isAttributionEnabled = true
            ready.addOnMapClickListener { point ->
                latestOnPick(Coordinate(point.latitude, point.longitude))
                true
            }
            ready.setOnMarkerClickListener { tapped ->
                val stopIndex = stopMarkers.indexOfFirst { it.id == tapped.id }
                val stopCallback = latestOnStopPick
                if (stopIndex >= 0 && stopCallback != null) {
                    stopCallback(stopIndex)
                    true
                } else {
                    val index = flowerMarkers.indexOfFirst { it.id == tapped.id }
                    val flower = shownFlowers.getOrNull(index)
                    if (flower != null) {
                        val overlap = if (stopCallback != null)
                            MapCameraGeometry.overlappingStopIndex(flower.coordinate, latestStops)
                        else null
                        if (overlap != null) stopCallback?.invoke(overlap)
                        else latestOnFlowerPick(flower)
                        true
                    } else false
                }
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
    LaunchedEffect(map, dark, freeMapStyle) {
        map?.setStyle(Style.Builder().fromUri(
            MapBasemapPolicy.openFreeMapStyleUrl(freeMapStyle, dark)
        )) {
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
    LaunchedEffect(map, styleVersion, current, currentIsMock) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        if (current == null) {
            liveMarker?.let { m.removeMarker(it) }
            liveMarker = null
        } else {
            val coordinate = LibreLatLng(current.latitude, current.longitude)
            if (liveMarker == null || liveStyleVersion != styleVersion || liveWasMock != currentIsMock) {
                liveMarker?.let { m.removeMarker(it) }
                val bitmap = RoutePinBitmap.current(context, currentIsMock)
                liveMarker = m.addMarker(LibreMarkerOptions().position(coordinate)
                    .icon(LibreIconFactory.getInstance(context).fromBitmap(bitmap))
                    .title(if (currentIsMock) "🐝 目前模擬位置" else "手機目前位置"))
                liveStyleVersion = styleVersion
                liveWasMock = currentIsMock
            } else liveMarker?.position = coordinate
        }
    }
    LaunchedEffect(map, styleVersion, start) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        if (start == null) {
            startMarker?.let { m.removeMarker(it) }
            startMarker = null
        } else {
            val position = LibreLatLng(start.latitude, start.longitude)
            if (startMarker == null || startStyleVersion != styleVersion) {
                startMarker?.let { m.removeMarker(it) }
                startMarker = m.addMarker(LibreMarkerOptions()
                    .position(position).title("📍 尋花起點"))
                startStyleVersion = styleVersion
            } else startMarker?.position = position
        }
    }
    LaunchedEffect(map, styleVersion, stops, road, previewRoad) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        stopMarkers.forEach { m.removeMarker(it) }
        line?.let { m.removePolyline(it) }
        stopMarkers = stops.mapIndexed { index, coordinate ->
            m.addMarker(LibreMarkerOptions()
                .position(LibreLatLng(coordinate.latitude, coordinate.longitude))
                .icon(LibreIconFactory.getInstance(context).fromBitmap(
                    RoutePinBitmap.create(context, index, stops.size)))
                .title("第 ${index + 1} 個目的地" +
                    if (index == stops.lastIndex) " · 終點" else " · 途經點"))
        }
        line = if (road.size >= 2) m.addPolyline(LibrePolylineOptions()
            .addAll(road.map { LibreLatLng(it.latitude, it.longitude) })
            .color(if (previewRoad) android.graphics.Color.rgb(42, 175, 211)
                else android.graphics.Color.rgb(232, 151, 39)).width(5f)) else null
    }
    LaunchedEffect(map, styleVersion, flowerSpots) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        flowerMarkers.forEach { m.removeMarker(it) }
        shownFlowers = flowerSpots.take(100)
        flowerMarkers = shownFlowers.map { flower ->
            m.addMarker(LibreMarkerOptions()
                .position(LibreLatLng(flower.coordinate.latitude, flower.coordinate.longitude))
                .title("🌸 " + flower.name +
                    if (flower.evidence == FlowerEvidence.PERSONALLY_CHECKED) " · 本人確認" else " · 待確認"))
        }
    }
    // Explicit camera actions only: do not follow the GPS marker at every tick.
    // The map layout needs non-zero dimensions before the bounds update is valid.
    LaunchedEffect(map, styleVersion, cameraRequest) {
        val m = map ?: return@LaunchedEffect
        val request = cameraRequest?.takeIf { it.target.hasAny } ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        view.post {
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0 || map !== m)
                return@post
            runCatching {
                val targets = request.target.points
                if (targets.size == 1) {
                    val only = targets.first()
                    m.animateCamera(LibreCameraUpdateFactory.newLatLngZoom(
                        LibreLatLng(only.latitude, only.longitude), 15.0,
                    ))
                } else {
                    val bounds = LibreLatLngBounds.Builder().includes(
                        targets.map { LibreLatLng(it.latitude, it.longitude) },
                    ).build()
                    val density = view.resources.displayMetrics.density
                    val side = (30f * density).toInt()
                    val top = (100f * density).toInt().coerceAtMost(view.height / 4)
                    val bottom = (95f * density).toInt().coerceAtMost(view.height / 4)
                    m.animateCamera(LibreCameraUpdateFactory.newLatLngBounds(
                        bounds, side, top, side, bottom,
                    ))
                }
            }.onFailure {
                latestOnCameraFailure("地圖無法調整顯示範圍，請手動移動或縮放")
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
private fun GoogleMapsPane(
    dark: Boolean, selected: Coordinate, current: Coordinate?, currentIsMock: Boolean,
    start: Coordinate?, stops: List<Coordinate>,
    road: List<Coordinate>, previewRoad: Boolean, flowerSpots: List<FlowerSpot>,
    cameraRequest: MapCameraRequest?, mapExplore: Boolean,
    onCameraFailure: (String) -> Unit,
    onPick: (Coordinate) -> Unit, onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)?, modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPick by rememberUpdatedState(onPick)
    val latestOnFlowerPick by rememberUpdatedState(onFlowerPick)
    val latestOnStopPick by rememberUpdatedState(onStopPick)
    val latestStops by rememberUpdatedState(stops)
    val latestOnCameraFailure by rememberUpdatedState(onCameraFailure)
    val view = remember(context) { GoogleMapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var currentMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var liveMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var liveWasMock by remember { mutableStateOf(false) }
    var startMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var stopMarkers by remember { mutableStateOf(emptyList<com.google.android.gms.maps.model.Marker>()) }
    var flowerMarkers by remember { mutableStateOf(emptyList<com.google.android.gms.maps.model.Marker>()) }
    var shownFlowers by remember { mutableStateOf(emptyList<FlowerSpot>()) }
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
            ready.uiSettings.isScrollGesturesEnabled = true
            ready.uiSettings.isZoomGesturesEnabled = true
            ready.uiSettings.isRotateGesturesEnabled = true
            ready.setOnMapClickListener { point ->
                latestOnPick(Coordinate(point.latitude, point.longitude))
            }
            ready.setOnMarkerClickListener { tapped ->
                val stopIndex = stopMarkers.indexOfFirst { it.id == tapped.id }
                val stopCallback = latestOnStopPick
                if (stopIndex >= 0 && stopCallback != null) {
                    stopCallback(stopIndex)
                    true
                } else {
                    val index = flowerMarkers.indexOfFirst { it.id == tapped.id }
                    val flower = shownFlowers.getOrNull(index)
                    if (flower != null) {
                        val overlap = if (stopCallback != null)
                            MapCameraGeometry.overlappingStopIndex(flower.coordinate, latestStops)
                        else null
                        if (overlap != null) stopCallback?.invoke(overlap)
                        else latestOnFlowerPick(flower)
                        true
                    } else false
                }
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
    LaunchedEffect(map, current, currentIsMock) {
        val m = map ?: return@LaunchedEffect
        if (current == null) {
            liveMarker?.remove()
            liveMarker = null
        } else {
            val coordinate = GoogleLatLng(current.latitude, current.longitude)
            if (liveMarker == null || liveWasMock != currentIsMock) {
                liveMarker?.remove()
                liveMarker = m.addMarker(GoogleMarkerOptions().position(coordinate)
                    .anchor(.5f, .5f).icon(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                            RoutePinBitmap.current(context, currentIsMock)))
                    .title(if (currentIsMock) "🐝 目前模擬位置" else "手機目前位置"))
                liveWasMock = currentIsMock
            } else liveMarker?.position = coordinate
        }
    }
    LaunchedEffect(map, start) {
        val m = map ?: return@LaunchedEffect
        if (start == null) {
            startMarker?.remove()
            startMarker = null
        } else {
            val position = GoogleLatLng(start.latitude, start.longitude)
            if (startMarker == null) {
                startMarker = m.addMarker(GoogleMarkerOptions()
                    .position(position).title("📍 尋花起點")
                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE)))
            } else startMarker?.position = position
        }
    }
    LaunchedEffect(map, stops, road, previewRoad) {
        val m = map ?: return@LaunchedEffect
        stopMarkers.forEach { it.remove() }
        line?.remove()
        stopMarkers = stops.mapIndexedNotNull { index, coordinate ->
            m.addMarker(GoogleMarkerOptions()
                .position(GoogleLatLng(coordinate.latitude, coordinate.longitude))
                .anchor(.5f, .5f)
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                    RoutePinBitmap.create(context, index, stops.size)))
                .title("第 ${index + 1} 個目的地" +
                    if (index == stops.lastIndex) " · 終點" else " · 途經點"))
        }
        line = if (road.size >= 2) m.addPolyline(GooglePolylineOptions()
            .addAll(road.map { GoogleLatLng(it.latitude, it.longitude) })
            .color(if (previewRoad) android.graphics.Color.rgb(42, 175, 211)
                else android.graphics.Color.rgb(232, 151, 39)).width(6f)) else null
    }
    LaunchedEffect(map, flowerSpots) {
        val m = map ?: return@LaunchedEffect
        flowerMarkers.forEach { it.remove() }
        // Google Maps may return null for a marker; keep the displayed spot paired
        // with its actual marker rather than indexing the original input list.
        val created = flowerSpots.take(100).mapNotNull { flower ->
            m.addMarker(GoogleMarkerOptions()
                .position(GoogleLatLng(flower.coordinate.latitude, flower.coordinate.longitude))
                .title("🌸 " + flower.name)
                .snippet(if (flower.evidence == FlowerEvidence.PERSONALLY_CHECKED)
                    "本人確認 · 非官方即時狀態" else "匯入候選 · 未確認")
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                    if (flower.evidence == FlowerEvidence.PERSONALLY_CHECKED)
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ROSE
                    else com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_VIOLET)))?.let {
                    it to flower
                }
        }
        flowerMarkers = created.map { it.first }
        shownFlowers = created.map { it.second }
    }
    LaunchedEffect(map, mapExplore) {
        val m = map ?: return@LaunchedEffect
        if (mapExplore) {
            val density = view.resources.displayMetrics.density
            m.setPadding(
                (18f * density).toInt(), (94f * density).toInt(),
                (18f * density).toInt(), (90f * density).toInt(),
            )
        } else m.setPadding(0, 0, 0, 0)
    }
    LaunchedEffect(map, cameraRequest) {
        val m = map ?: return@LaunchedEffect
        val request = cameraRequest?.takeIf { it.target.hasAny } ?: return@LaunchedEffect
        view.post {
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0 || map !== m)
                return@post
            runCatching {
                val targets = request.target.points
                if (targets.size == 1) {
                    val only = targets.first()
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        GoogleLatLng(only.latitude, only.longitude), 15f,
                    ))
                } else {
                    val builder = GoogleLatLngBounds.Builder()
                    targets.forEach { builder.include(GoogleLatLng(it.latitude, it.longitude)) }
                    val padding = (34f * view.resources.displayMetrics.density).toInt()
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
                }
            }.onFailure {
                latestOnCameraFailure("地圖無法調整顯示範圍，請手動移動或縮放")
            }
        }
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
