package com.naos.pikminbee.ui

import android.view.View
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
import com.naos.pikminbee.domain.WalkingGeometry
import com.naos.pikminbee.domain.RouteMapSegments
import com.naos.pikminbee.flower.FlowerSpot
import com.naos.pikminbee.flower.FlowerEvidence
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView as LibreMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import com.naos.pikminbee.data.BrightMapPalette
import org.maplibre.android.camera.CameraPosition as LibreCameraPosition
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.annotations.IconFactory as LibreIconFactory
import org.maplibre.android.annotations.MarkerOptions as LibreMarkerOptions
import org.maplibre.android.annotations.PolylineOptions as LibrePolylineOptions
import org.maplibre.android.geometry.LatLngBounds as LibreLatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory as LibreCameraUpdateFactory

/** A one-shot camera instruction, including updates while the user explicitly enables following. */
data class MapCameraRequest(
    val id: Int,
    val target: MapCameraGeometry.ViewTarget,
)

/** Retain the camera independently of selected/GPS coordinates during settings changes. */
data class MapViewport(val point: Coordinate, val zoom: Double, val bearing: Double, val tilt: Double)

private fun overlapsCurrent(point: Coordinate?, current: Coordinate?): Boolean =
    point != null && current != null && WalkingGeometry.distanceMeters(point,current) <= 3.0

/** Both native providers consume precisely the same picked stops and real road geometry. */
@Composable
fun LocationMap(
    provider: MapProvider,
    dark: Boolean,
    selected: Coordinate,
    selectionActive: Boolean,
    current: Coordinate?,
    currentIsMock: Boolean,
    start: Coordinate?,
    stops: List<Coordinate>,
    road: List<Coordinate>,
    routeSegments: RouteMapSegments = RouteMapSegments(),
    previewRoad: Boolean,
    flowerSpots: List<FlowerSpot>,
    cameraRequest: MapCameraRequest? = null,
    viewport: MapViewport? = null,
    onViewport: (MapViewport) -> Unit = {},
    onCameraApplied: (Int) -> Unit = {},
    mapExplore: Boolean = false,
    onCameraFailure: (String) -> Unit = {},
    onCameraGesture: () -> Unit = {},
    onPick: (Coordinate) -> Unit,
    onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    when (provider) {
        MapProvider.OPEN_FREE_MAP -> OpenFreeMapPane(dark, selected, selectionActive, current, currentIsMock, start, stops, road, routeSegments, previewRoad, flowerSpots,
            cameraRequest, viewport, onViewport, onCameraApplied, mapExplore, onCameraFailure, onCameraGesture,
            onPick, onFlowerPick, onStopPick, modifier, interactive)
        MapProvider.GOOGLE_MAPS -> if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
            GoogleMapsPane(dark, selected, selectionActive, current, currentIsMock, start, stops, road, routeSegments, previewRoad, flowerSpots,
                cameraRequest, viewport, onViewport, onCameraApplied, mapExplore, onCameraFailure, onCameraGesture, onPick, onFlowerPick, onStopPick, modifier, interactive)
        } else {
            OpenFreeMapPane(dark, selected, selectionActive, current, currentIsMock, start, stops, road, routeSegments, previewRoad, flowerSpots,
                cameraRequest, viewport, onViewport, onCameraApplied, mapExplore, onCameraFailure, onCameraGesture,
                onPick, onFlowerPick, onStopPick, modifier, interactive)
        }
    }
}

@Composable
private fun OpenFreeMapPane(
    dark: Boolean, selected: Coordinate, selectionActive: Boolean,
    current: Coordinate?, currentIsMock: Boolean,
    start: Coordinate?, stops: List<Coordinate>,
    road: List<Coordinate>, routeSegments: RouteMapSegments, previewRoad: Boolean, flowerSpots: List<FlowerSpot>,
    cameraRequest: MapCameraRequest?, viewport: MapViewport?,
    onViewport: (MapViewport) -> Unit, onCameraApplied: (Int) -> Unit, mapExplore: Boolean,
    onCameraFailure: (String) -> Unit,
    onCameraGesture: () -> Unit,
    onPick: (Coordinate) -> Unit, onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)?,
    modifier: Modifier,
    interactive: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestInteractive by rememberUpdatedState(interactive)
    val latestOnPick by rememberUpdatedState(onPick)
    val latestOnFlowerPick by rememberUpdatedState(onFlowerPick)
    val latestOnStopPick by rememberUpdatedState(onStopPick)
    val latestStops by rememberUpdatedState(stops)
    val latestOnCameraFailure by rememberUpdatedState(onCameraFailure)
    val latestOnCameraGesture by rememberUpdatedState(onCameraGesture)
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewport by rememberUpdatedState(onViewport)
    val latestCameraRequest by rememberUpdatedState(cameraRequest)
    val latestOnCameraApplied by rememberUpdatedState(onCameraApplied)
    val showSelection = selectionActive && !overlapsCurrent(selected,current)
    val showStart = start != null && !overlapsCurrent(start,current)
    val obscuredStops = stops.indices.filter { overlapsCurrent(stops[it],current) }
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
    var stopMarkers by remember { mutableStateOf(emptyList<Pair<Int, org.maplibre.android.annotations.Marker>>()) }
    var flowerMarkers by remember { mutableStateOf(emptyList<org.maplibre.android.annotations.Marker>()) }
    var shownFlowers by remember { mutableStateOf(emptyList<FlowerSpot>()) }
    var line by remember { mutableStateOf<org.maplibre.android.annotations.Polyline?>(null) }
    var legLines by remember { mutableStateOf(emptyList<org.maplibre.android.annotations.Polyline>()) }
    DisposableEffect(view, lifecycleOwner) {
        var disposed = false
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
            if (disposed) return@getMapAsync
            map = ready
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isScrollGesturesEnabled = latestInteractive
            ready.uiSettings.isZoomGesturesEnabled = latestInteractive
            ready.uiSettings.isRotateGesturesEnabled = latestInteractive
            ready.uiSettings.isTiltGesturesEnabled = latestInteractive
            ready.uiSettings.isAttributionEnabled = true
            ready.addOnMapClickListener { point ->
                if (latestInteractive) latestOnPick(Coordinate(point.latitude, point.longitude))
                true
            }
            ready.setOnMarkerClickListener { tapped ->
                if (!latestInteractive) return@setOnMarkerClickListener true
                val stopIndex = stopMarkers.firstOrNull { it.second.id == tapped.id }?.first ?: -1
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
            val pose = latestViewport
            val target = pose?.point ?: selected
            ready.moveCamera(LibreCameraUpdateFactory.newCameraPosition(
                LibreCameraPosition.Builder()
                    .target(LibreLatLng(target.latitude,target.longitude))
                    .zoom(pose?.zoom ?: 14.5)
                    .bearing(pose?.bearing ?: 0.0)
                    .tilt(pose?.tilt ?: 0.0).build()
            ))
            ready.addOnCameraMoveStartedListener { reason ->
                if (!disposed && reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)
                    latestOnCameraGesture()
            }
            ready.addOnCameraIdleListener {
                if (!disposed) {
                    val camera = ready.cameraPosition
                    camera.target?.let {
                        runCatching { Coordinate(it.latitude,it.longitude) }.onSuccess { point ->
                            latestOnViewport(MapViewport(point,camera.zoom,camera.bearing,camera.tilt))
                        }
                    }
                }
            }
        }
        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            map = null
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, interactive) {
        map?.uiSettings?.apply {
            isScrollGesturesEnabled = interactive
            isZoomGesturesEnabled = interactive
            isRotateGesturesEnabled = interactive
            isTiltGesturesEnabled = interactive
        }
    }
    LaunchedEffect(map, dark) {
        val loadedMap = map ?: return@LaunchedEffect
        val styleUrl = MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.BRIGHT, dark)
        loadedMap.setStyle(Style.Builder().fromUri(styleUrl)) { loadedStyle ->
            if (dark) runCatching { BrightMapPalette.applyDark(context, loadedStyle) }
                .onFailure { latestOnCameraFailure("無法完整套用深色地圖，請重試") }
            styleVersion++
        }
    }
    // The moving position is updated in place. Do not recreate the whole route every second.
    LaunchedEffect(map, styleVersion, selected, showSelection) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        if (!showSelection) {
            currentMarker?.let { m.removeMarker(it) }
            currentMarker = null
        } else {
            val position = LibreLatLng(selected.latitude, selected.longitude)
            if (currentMarker == null || currentStyleVersion != styleVersion) {
                currentMarker?.let { m.removeMarker(it) }
                currentMarker = m.addMarker(LibreMarkerOptions()
                    .position(position).title("選取位置"))
                currentStyleVersion = styleVersion
            } else currentMarker?.position = position
        }
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
                    .title(if (currentIsMock) "目前模擬位置" else "手機目前位置"))
                liveStyleVersion = styleVersion
                liveWasMock = currentIsMock
            } else liveMarker?.position = coordinate
        }
    }
    LaunchedEffect(map, styleVersion, start, showStart) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        if (!showStart || start == null) {
            startMarker?.let { m.removeMarker(it) }
            startMarker = null
        } else {
            val position = LibreLatLng(start.latitude, start.longitude)
            if (startMarker == null || startStyleVersion != styleVersion) {
                startMarker?.let { m.removeMarker(it) }
                startMarker = m.addMarker(LibreMarkerOptions()
                    .position(position).title("路線起點"))
                startStyleVersion = styleVersion
            } else startMarker?.position = position
        }
    }
    LaunchedEffect(map, styleVersion, stops, road, routeSegments, previewRoad, obscuredStops) {
        val m = map ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        stopMarkers.forEach { m.removeMarker(it.second) }
        legLines.forEach { m.removePolyline(it) }
        legLines = emptyList()
        line?.let { m.removePolyline(it) }
        stopMarkers = stops.mapIndexedNotNull { index, coordinate ->
            if (index in obscuredStops) return@mapIndexedNotNull null
            index to m.addMarker(LibreMarkerOptions()
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
        fun addSegment(points: List<Coordinate>, color: Int, width: Float) =
            m.addPolyline(LibrePolylineOptions().addAll(points.map { LibreLatLng(it.latitude, it.longitude) })
                .color(color).width(width))
        legLines = buildList {
            if (routeSegments.completed.size >= 2) add(addSegment(routeSegments.completed, android.graphics.Color.rgb(137, 150, 163), 5f))
            if (routeSegments.active.size >= 2) {
                add(addSegment(routeSegments.active, android.graphics.Color.WHITE, 12f))
                add(addSegment(routeSegments.active, android.graphics.Color.rgb(8, 123, 230), 8f))
            }
        }
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
    // Camera changes require an explicit action or an explicitly enabled follow mode.
    // The map layout needs non-zero dimensions before the bounds update is valid.
    LaunchedEffect(map, styleVersion, cameraRequest) {
        val m = map ?: return@LaunchedEffect
        val request = cameraRequest?.takeIf { it.target.hasAny } ?: return@LaunchedEffect
        if (styleVersion == 0) return@LaunchedEffect
        view.post {
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0 ||
                map !== m || latestCameraRequest?.id != request.id)
                return@post
            runCatching {
                val targets = request.target.points
                if (targets.size == 1) {
                    val only = targets.first()
                    m.cancelTransitions()
                    m.moveCamera(LibreCameraUpdateFactory.newLatLngZoom(
                        LibreLatLng(only.latitude, only.longitude), maxOf(15.0, m.cameraPosition.zoom),
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
            }.onSuccess { latestOnCameraApplied(request.id) }.onFailure {
                latestOnCameraFailure("地圖無法調整顯示範圍，請手動移動或縮放")
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { native ->
        native.importantForAccessibility = if (interactive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    })
}

@Composable
private fun GoogleMapsPane(
    dark: Boolean, selected: Coordinate, selectionActive: Boolean,
    current: Coordinate?, currentIsMock: Boolean,
    start: Coordinate?, stops: List<Coordinate>,
    road: List<Coordinate>, routeSegments: RouteMapSegments, previewRoad: Boolean, flowerSpots: List<FlowerSpot>,
    cameraRequest: MapCameraRequest?, viewport: MapViewport?,
    onViewport: (MapViewport) -> Unit, onCameraApplied: (Int) -> Unit, mapExplore: Boolean,
    onCameraFailure: (String) -> Unit,
    onCameraGesture: () -> Unit,
    onPick: (Coordinate) -> Unit, onFlowerPick: (FlowerSpot) -> Unit,
    onStopPick: ((Int) -> Unit)?,
    modifier: Modifier,
    interactive: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestInteractive by rememberUpdatedState(interactive)
    val latestOnPick by rememberUpdatedState(onPick)
    val latestOnFlowerPick by rememberUpdatedState(onFlowerPick)
    val latestOnStopPick by rememberUpdatedState(onStopPick)
    val latestStops by rememberUpdatedState(stops)
    val latestOnCameraFailure by rememberUpdatedState(onCameraFailure)
    val latestOnCameraGesture by rememberUpdatedState(onCameraGesture)
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewport by rememberUpdatedState(onViewport)
    val latestCameraRequest by rememberUpdatedState(cameraRequest)
    val latestOnCameraApplied by rememberUpdatedState(onCameraApplied)
    val showSelection = selectionActive && !overlapsCurrent(selected,current)
    val showStart = start != null && !overlapsCurrent(start,current)
    val obscuredStops = stops.indices.filter { overlapsCurrent(stops[it],current) }
    val view = remember(context) { GoogleMapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var currentMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var liveMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var liveWasMock by remember { mutableStateOf(false) }
    var startMarker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var stopMarkers by remember { mutableStateOf(emptyList<Pair<Int, com.google.android.gms.maps.model.Marker>>()) }
    var flowerMarkers by remember { mutableStateOf(emptyList<com.google.android.gms.maps.model.Marker>()) }
    var shownFlowers by remember { mutableStateOf(emptyList<FlowerSpot>()) }
    var line by remember { mutableStateOf<com.google.android.gms.maps.model.Polyline?>(null) }
    var legLines by remember { mutableStateOf(emptyList<com.google.android.gms.maps.model.Polyline>()) }
    DisposableEffect(view, lifecycleOwner) {
        var disposed = false
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
            if (disposed) return@getMapAsync
            map = ready
            ready.uiSettings.isMapToolbarEnabled = false
            ready.uiSettings.isScrollGesturesEnabled = latestInteractive
            ready.uiSettings.isZoomGesturesEnabled = latestInteractive
            ready.uiSettings.isRotateGesturesEnabled = latestInteractive
            ready.uiSettings.isTiltGesturesEnabled = latestInteractive
            ready.setOnMapClickListener { point ->
                if (latestInteractive) latestOnPick(Coordinate(point.latitude, point.longitude))
            }
            ready.setOnMarkerClickListener { tapped ->
                if (!latestInteractive) return@setOnMarkerClickListener true
                val stopIndex = stopMarkers.firstOrNull { it.second.id == tapped.id }?.first ?: -1
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
            val pose = latestViewport
            val target = pose?.point ?: selected
            ready.moveCamera(CameraUpdateFactory.newCameraPosition(
                com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(GoogleLatLng(target.latitude,target.longitude))
                    .zoom(pose?.zoom?.toFloat() ?: 14.5f)
                    .bearing(pose?.bearing?.toFloat() ?: 0f)
                    .tilt(pose?.tilt?.toFloat() ?: 0f).build()
            ))
            ready.setOnCameraMoveStartedListener { reason ->
                if (!disposed && reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)
                    latestOnCameraGesture()
            }
            ready.setOnCameraIdleListener {
                if (!disposed) {
                    val camera = ready.cameraPosition
                    runCatching { Coordinate(camera.target.latitude,camera.target.longitude) }.onSuccess { point ->
                        latestOnViewport(MapViewport(point,camera.zoom.toDouble(),
                            camera.bearing.toDouble(),camera.tilt.toDouble()))
                    }
                }
            }
        }
        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            map = null
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, interactive) {
        map?.uiSettings?.apply {
            isScrollGesturesEnabled = interactive
            isZoomGesturesEnabled = interactive
            isRotateGesturesEnabled = interactive
            isTiltGesturesEnabled = interactive
        }
    }
    LaunchedEffect(map, dark) {
        map?.setMapStyle(if (dark) MapStyleOptions(GOOGLE_DARK_STYLE) else null)
    }
    LaunchedEffect(map, selected, showSelection) {
        val m = map ?: return@LaunchedEffect
        if (!showSelection) {
            currentMarker?.remove()
            currentMarker = null
        } else {
            val position = GoogleLatLng(selected.latitude, selected.longitude)
            if (currentMarker == null) {
                currentMarker = m.addMarker(GoogleMarkerOptions()
                    .position(position).title("選取位置"))
            } else currentMarker?.position = position
        }
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
                    .anchor(.5f, .5f).zIndex(10f).icon(
                        com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                            RoutePinBitmap.current(context, currentIsMock)))
                    .title(if (currentIsMock) "目前模擬位置" else "手機目前位置"))
                liveWasMock = currentIsMock
            } else liveMarker?.position = coordinate
        }
    }
    LaunchedEffect(map, start, showStart) {
        val m = map ?: return@LaunchedEffect
        if (!showStart || start == null) {
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
    LaunchedEffect(map, stops, road, routeSegments, previewRoad, obscuredStops) {
        val m = map ?: return@LaunchedEffect
        stopMarkers.forEach { it.second.remove() }
        legLines.forEach { it.remove() }
        legLines = emptyList()
        line?.remove()
        stopMarkers = stops.mapIndexedNotNull { index, coordinate ->
            if (index in obscuredStops) return@mapIndexedNotNull null
            m.addMarker(GoogleMarkerOptions()
                .position(GoogleLatLng(coordinate.latitude, coordinate.longitude))
                .anchor(.5f, .5f)
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(
                    RoutePinBitmap.create(context, index, stops.size)))
                .title("第 ${index + 1} 個目的地" +
                    if (index == stops.lastIndex) " · 終點" else " · 途經點"))?.let { index to it }
        }
        line = if (road.size >= 2) m.addPolyline(GooglePolylineOptions()
            .addAll(road.map { GoogleLatLng(it.latitude, it.longitude) })
            .color(if (previewRoad) android.graphics.Color.rgb(42, 175, 211)
                else android.graphics.Color.rgb(232, 151, 39)).width(6f)) else null
        fun addSegment(points: List<Coordinate>, color: Int, width: Float, z: Float) =
            m.addPolyline(GooglePolylineOptions().addAll(points.map { GoogleLatLng(it.latitude, it.longitude) })
                .color(color).width(width).zIndex(z))
        legLines = buildList {
            if (routeSegments.completed.size >= 2) add(addSegment(routeSegments.completed, android.graphics.Color.rgb(137, 150, 163), 6f, 1f))
            if (routeSegments.active.size >= 2) {
                add(addSegment(routeSegments.active, android.graphics.Color.WHITE, 13f, 2f))
                add(addSegment(routeSegments.active, android.graphics.Color.rgb(8, 123, 230), 9f, 3f))
            }
        }
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
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0 ||
                map !== m || latestCameraRequest?.id != request.id)
                return@post
            runCatching {
                val targets = request.target.points
                if (targets.size == 1) {
                    val only = targets.first()
                    m.stopAnimation()
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        GoogleLatLng(only.latitude, only.longitude), maxOf(15f,m.cameraPosition.zoom),
                    ))
                } else {
                    val builder = GoogleLatLngBounds.Builder()
                    targets.forEach { builder.include(GoogleLatLng(it.latitude, it.longitude)) }
                    val padding = (34f * view.resources.displayMetrics.density).toInt()
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
                }
            }.onSuccess { latestOnCameraApplied(request.id) }.onFailure {
                latestOnCameraFailure("地圖無法調整顯示範圍，請手動移動或縮放")
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = { native ->
        native.importantForAccessibility = if (interactive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    })
}

private const val GOOGLE_DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1b2433"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#afbed0"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#152031"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#35435a"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0c2840"}]},
 {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#263447"}]}
]"""
