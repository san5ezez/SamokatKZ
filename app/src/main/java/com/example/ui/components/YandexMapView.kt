package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.model.NavigatorUiState
import com.example.model.RouteSegmentKind
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.traffic.TrafficLayer
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference

@Composable
fun YandexMapView(
  uiState: NavigatorUiState,
  isDarkMap: Boolean,
  onMapLongTap: (Point) -> Unit,
  onVisibleRegionChanged: (BoundingBox) -> Unit,
  onRouteTapped: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val currentOnLongTap by rememberUpdatedState(onMapLongTap)
  val currentOnVisibleRegion by rememberUpdatedState(onVisibleRegionChanged)
  val currentOnRouteTapped by rememberUpdatedState(onRouteTapped)

  val mapView = remember { MapView(context) }
  var trafficLayer by remember { mutableStateOf<TrafficLayer?>(null) }
  var userLocationLayer by remember { mutableStateOf<UserLocationLayer?>(null) }

  // Strong references required because MapKit holds WeakReferences to listeners
  val tapListenersHolder = remember { mutableListOf<MapObjectTapListener>() }
  val inputListener = remember {
    object : InputListener {
      override fun onMapTap(map: Map, point: Point) = Unit
      override fun onMapLongTap(map: Map, point: Point) {
        currentOnLongTap(point)
      }
    }
  }
  val cameraListener = remember {
    CameraListener { map, _, reason, finished ->
      if (finished || reason == CameraUpdateReason.GESTURES) {
        val vr = map.visibleRegion
        val minLat =
          minOf(
            vr.bottomLeft.latitude,
            vr.bottomRight.latitude,
            vr.topLeft.latitude,
            vr.topRight.latitude,
          )
        val maxLat =
          maxOf(
            vr.bottomLeft.latitude,
            vr.bottomRight.latitude,
            vr.topLeft.latitude,
            vr.topRight.latitude,
          )
        val minLon =
          minOf(
            vr.bottomLeft.longitude,
            vr.bottomRight.longitude,
            vr.topLeft.longitude,
            vr.topRight.longitude,
          )
        val maxLon =
          maxOf(
            vr.bottomLeft.longitude,
            vr.bottomRight.longitude,
            vr.topLeft.longitude,
            vr.topRight.longitude,
          )
        currentOnVisibleRegion(BoundingBox(Point(minLat, minLon), Point(maxLat, maxLon)))
      }
    }
  }

  val originPinBitmap = remember { MapBitmapUtils.createOriginScooterPin(context) }
  val destPinBitmap = remember { MapBitmapUtils.createDestinationPin(context) }

  DisposableEffect(lifecycleOwner, mapView) {
    val map = mapView.mapWindow.map
    map.move(
      CameraPosition(NavigatorUiState.ALMATY_CENTER, 13.8f, 0.0f, 0.0f),
      Animation(Animation.Type.SMOOTH, 0.4f),
      null,
    )
    map.addInputListener(WeakReference(inputListener))
    map.addCameraListener(WeakReference(cameraListener))

    runCatching {
      val mk = MapKitFactory.getInstance()
      trafficLayer = mk.createTrafficLayer(mapView.mapWindow)
      userLocationLayer = mk.createUserLocationLayer(mapView.mapWindow)
    }

    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          runCatching {
            MapKitFactory.getInstance().onStart()
            mapView.onStart()
          }
        }
        Lifecycle.Event.ON_STOP -> {
          runCatching {
            mapView.onStop()
            MapKitFactory.getInstance().onStop()
          }
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      map.removeInputListener(WeakReference(inputListener))
      map.removeCameraListener(WeakReference(cameraListener))
      runCatching {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
      }
    }
  }

  // Sync night mode, traffic layer, and user location layer
  LaunchedEffect(isDarkMap, uiState.layers.trafficEnabled, uiState.locationPermissionGranted) {
    mapView.mapWindow.map.isNightModeEnabled = isDarkMap
    trafficLayer?.isTrafficVisible = uiState.layers.trafficEnabled
    userLocationLayer?.isVisible = uiState.locationPermissionGranted
  }

  // Handle explicit camera moves (My Location & Zoom +/-)
  LaunchedEffect(uiState.cameraActionNonce) {
    if (uiState.cameraActionNonce == 0L) return@LaunchedEffect
    val map = mapView.mapWindow.map
    val current = map.cameraPosition
    val target = uiState.cameraTarget ?: current.target
    val newZoom =
      if (uiState.cameraZoomDelta != null) {
        (current.zoom + uiState.cameraZoomDelta).coerceIn(3f, 20f)
      } else {
        15.5f
      }
    map.move(
      CameraPosition(target, newZoom, 0f, 0f),
      Animation(Animation.Type.SMOOTH, 0.35f),
      null,
    )
  }

  // Fit camera bounds to selected route
  LaunchedEffect(uiState.fitRouteBoundsNonce) {
    if (uiState.fitRouteBoundsNonce == 0L) return@LaunchedEffect
    val pts = uiState.selectedRoute?.allPoints.orEmpty()
    if (pts.size < 2) return@LaunchedEffect

    val minLat = pts.minOf { it.latitude }
    val maxLat = pts.maxOf { it.latitude }
    val minLon = pts.minOf { it.longitude }
    val maxLon = pts.maxOf { it.longitude }
    val latPad = ((maxLat - minLat) * 0.25).coerceAtLeast(0.004)
    val lonPad = ((maxLon - minLon) * 0.18).coerceAtLeast(0.004)
    // Add extra bottom latitude padding so route stays visible above the BottomSheet
    val bbox =
      BoundingBox(
        Point(minLat - latPad * 1.4, minLon - lonPad),
        Point(maxLat + latPad * 0.6, maxLon + lonPad),
      )
    val map = mapView.mapWindow.map
    val pos = map.cameraPosition(Geometry.fromBoundingBox(bbox))
    map.move(
      CameraPosition(pos.target, (pos.zoom - 0.2f).coerceIn(10.5f, 16.8f), 0f, 0f),
      Animation(Animation.Type.SMOOTH, 0.45f),
      null,
    )
  }

  // Render routes, pins, and road hazards
  LaunchedEffect(
    uiState.routes,
    uiState.selectedRouteId,
    uiState.originPoint,
    uiState.destinationPoint,
    uiState.roadHazards,
    uiState.layers.roadEventsEnabled,
  ) {
    val mapObjects = mapView.mapWindow.map.mapObjects
    mapObjects.clear()
    tapListenersHolder.clear()

    val selectedId = uiState.selectedRoute?.id

    // 1. Unselected alternative routes (muted, tappable)
    uiState.routes
      .filter { it.id != selectedId }
      .forEach { altRoute ->
        if (altRoute.allPoints.size >= 2) {
          val polyObj = mapObjects.addPolyline(Polyline(altRoute.allPoints))
          polyObj.strokeWidth = 5.5f
          polyObj.setStrokeColor(0x8864748B.toInt())
          polyObj.outlineWidth = 1.0f
          polyObj.outlineColor = 0xAAFFFFFF.toInt()
          polyObj.zIndex = 1f
          val listener = MapObjectTapListener { _, _ ->
            currentOnRouteTapped(altRoute.id)
            true
          }
          tapListenersHolder.add(listener)
          polyObj.addTapListener(WeakReference(listener))
        }
      }

    // 2. Active selected route with PDD KZ segment coloring
    uiState.selectedRoute?.segments?.forEach { seg ->
      if (seg.points.size >= 2) {
        val polyObj = mapObjects.addPolyline(Polyline(seg.points))
        polyObj.strokeWidth = 7.5f
        polyObj.outlineWidth = 1.8f
        polyObj.outlineColor = 0xEEFFFFFF.toInt()
        polyObj.zIndex = 4f
        when (seg.kind) {
          RouteSegmentKind.BIKE_LANE -> {
            polyObj.setStrokeColor(0xFF00B368.toInt())
          }
          RouteSegmentKind.HAZARD_DETOUR -> {
            polyObj.setStrokeColor(0xFF10D17D.toInt())
          }
          RouteSegmentKind.PEDESTRIAN_DISMOUNT -> {
            polyObj.setStrokeColor(0xFF2979FF.toInt())
            polyObj.dashLength = 14f
            polyObj.gapLength = 9f
          }
          RouteSegmentKind.SIDEWALK_SHORTCUT -> {
            polyObj.setStrokeColor(0xFFFF9500.toInt())
            polyObj.dashLength = 16f
            polyObj.gapLength = 7f
          }
        }
      }
    }

    // 3. Road hazard markers (accidents, closures, severe traffic)
    if (uiState.layers.roadEventsEnabled) {
      uiState.roadHazards.forEach { hazard ->
        val pin = mapObjects.addPlacemark()
        pin.geometry = hazard.point
        pin.zIndex = 6f
        pin.setIcon(
          ImageProvider.fromBitmap(MapBitmapUtils.createHazardPin(context, hazard.type)),
          IconStyle().setAnchor(android.graphics.PointF(0.5f, 0.5f)),
        )
      }
    }

    // 4. Origin scooter marker
    val originMark = mapObjects.addPlacemark()
    originMark.geometry = uiState.originPoint
    originMark.zIndex = 7f
    originMark.setIcon(
      ImageProvider.fromBitmap(originPinBitmap),
      IconStyle().setAnchor(android.graphics.PointF(0.5f, 0.5f)),
    )

    // 5. Destination marker
    uiState.destinationPoint?.let { dest ->
      val destMark = mapObjects.addPlacemark()
      destMark.geometry = dest
      destMark.zIndex = 8f
      destMark.setIcon(
        ImageProvider.fromBitmap(destPinBitmap),
        IconStyle().setAnchor(android.graphics.PointF(0.5f, 0.92f)),
      )
    }
  }

  AndroidView(
    factory = { mapView },
    modifier = modifier,
  )
}
