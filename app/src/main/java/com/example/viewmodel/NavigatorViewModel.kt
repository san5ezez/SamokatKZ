package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SamokatApplication
import com.example.data.LocationRepository
import com.example.data.PreferencesRepository
import com.example.data.ScooterRoutingRepository
import com.example.data.SearchRepository
import com.example.model.NavigatorUiState
import com.example.model.RideMode
import com.example.model.RouteSegmentKind
import com.example.model.SearchSuggestion
import com.example.model.SpeedLimitZone
import com.example.model.ThemePreference
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NavigatorViewModel(application: Application) : AndroidViewModel(application) {

  private val preferencesRepository = PreferencesRepository(application)
  private val locationRepository = LocationRepository(application)
  private val searchRepository = SearchRepository()
  private val routingRepository = ScooterRoutingRepository()

  private val _uiState =
    MutableStateFlow(
      NavigatorUiState(
        isMapKitKeyConfigured = SamokatApplication.isMapKitConfigured,
        pddAlerts = buildPddAlerts(RideMode.FULL_LEGAL, SpeedLimitZone.BIKE_LANE),
      )
    )
  val uiState: StateFlow<NavigatorUiState> = _uiState.asStateFlow()

  private var searchDebounceJob: Job? = null
  private var routingJob: Job? = null
  private var locationStreamJob: Job? = null
  private var visibleBoundingBox: BoundingBox = SearchRepository.ALMATY_BOUNDING_BOX

  init {
    observePreferences()
  }

  private fun observePreferences() {
    viewModelScope.launch {
      preferencesRepository.layersFlow.collect { layers ->
        _uiState.update { it.copy(layers = layers) }
      }
    }
    viewModelScope.launch {
      preferencesRepository.rideModeFlow.collect { savedMode ->
        val changed = _uiState.value.rideMode != savedMode
        _uiState.update { state ->
          state.copy(
            rideMode = savedMode,
            pddAlerts = buildPddAlerts(savedMode, state.activeSpeedZone),
          )
        }
        if (changed && _uiState.value.destinationPoint != null) {
          recalculateRoutes()
        }
      }
    }
  }

  fun onLocationPermissionUpdated(granted: Boolean) {
    _uiState.update { it.copy(locationPermissionGranted = granted) }
    if (!granted) {
      _uiState.update {
        it.copy(
          originPoint = NavigatorUiState.ALMATY_CENTER,
          isUsingRealGps = false,
        )
      }
      return
    }

    viewModelScope.launch {
      val reading = locationRepository.getCurrentLocation()
      if (reading != null) {
        _uiState.update {
          it.copy(
            originPoint = reading.point,
            currentSpeedKmh = reading.speedKmh,
            isUsingRealGps = true,
            cameraTarget = reading.point,
            cameraActionNonce = it.cameraActionNonce + 1,
          )
        }
      }
    }

    locationStreamJob?.cancel()
    locationStreamJob =
      viewModelScope.launch {
        locationRepository
          .locationUpdatesFlow()
          .catch { /* keep Almaty fallback if provider is unavailable */ }
          .collect { reading ->
            _uiState.update { state ->
              val speedZone = inferSpeedZone(state)
              state.copy(
                originPoint = reading.point,
                currentSpeedKmh = reading.speedKmh,
                isUsingRealGps = true,
                activeSpeedZone = speedZone,
                pddAlerts = buildPddAlerts(state.rideMode, speedZone, reading.speedKmh),
              )
            }
          }
      }
  }

  fun updateVisibleRegion(boundingBox: BoundingBox) {
    visibleBoundingBox = boundingBox
  }

  fun onSearchQueryChanged(query: String) {
    _uiState.update { it.copy(searchQuery = query, isSearchFocused = true) }
    searchDebounceJob?.cancel()
    if (query.isBlank()) {
      _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
      return
    }

    searchDebounceJob =
      viewModelScope.launch {
        delay(280L)
        _uiState.update { it.copy(isSearching = true) }
        val result =
          searchRepository.fetchSuggestions(
            query = query,
            userPoint = _uiState.value.originPoint,
            boundingBox = visibleBoundingBox,
          )
        result
          .onSuccess { items ->
            _uiState.update { it.copy(suggestions = items, isSearching = false) }
          }
          .onFailure {
            _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
          }
      }
  }

  fun onQuickCategorySelected(categoryQuery: String) {
    _uiState.update { it.copy(searchQuery = categoryQuery, isSearchFocused = true, isSearching = true) }
    searchDebounceJob?.cancel()
    searchDebounceJob =
      viewModelScope.launch {
        searchRepository
          .searchTopPlaces(categoryQuery, visibleBoundingBox)
          .onSuccess { items ->
            _uiState.update { it.copy(suggestions = items, isSearching = false) }
          }
          .onFailure {
            _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
          }
      }
  }

  fun onSearchFocusChanged(focused: Boolean) {
    _uiState.update { it.copy(isSearchFocused = focused) }
  }

  fun onSuggestionSelected(suggestion: SearchSuggestion) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          searchQuery = suggestion.title,
          isSearchFocused = false,
          suggestions = emptyList(),
          isRouting = true,
          routingError = null,
        )
      }
      val resolved = searchRepository.resolveSuggestionPoint(suggestion)
      if (resolved != null) {
        setDestinationAndRoute(resolved, suggestion.title)
      } else {
        _uiState.update {
          it.copy(
            isRouting = false,
            routingError = "Не удалось определить координаты для «${suggestion.title}»",
          )
        }
      }
    }
  }

  fun onMapLongTap(point: Point) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          destinationPoint = point,
          destinationTitle = "Точка на карте",
          isSearchFocused = false,
          suggestions = emptyList(),
          isRouting = true,
          routingError = null,
        )
      }
      val address = searchRepository.reverseGeocode(point)
      _uiState.update { it.copy(destinationTitle = address, searchQuery = address) }
      recalculateRoutes()
    }
  }

  private fun setDestinationAndRoute(point: Point, title: String) {
    _uiState.update {
      it.copy(
        destinationPoint = point,
        destinationTitle = title,
        isNavigating = false,
      )
    }
    recalculateRoutes()
  }

  fun onRideModeSelected(mode: RideMode) {
    if (_uiState.value.rideMode == mode) return
    viewModelScope.launch { preferencesRepository.setRideMode(mode) }
    _uiState.update {
      it.copy(
        rideMode = mode,
        pddAlerts = buildPddAlerts(mode, it.activeSpeedZone, it.currentSpeedKmh),
      )
    }
    if (_uiState.value.destinationPoint != null) {
      recalculateRoutes()
    }
  }

  fun onRouteOptionSelected(routeId: String) {
    _uiState.update { state ->
      val chosen = state.routes.firstOrNull { it.id == routeId }
      val firstSeg = chosen?.segments?.firstOrNull()?.kind
      val zone =
        if (firstSeg == RouteSegmentKind.PEDESTRIAN_DISMOUNT ||
          firstSeg == RouteSegmentKind.SIDEWALK_SHORTCUT
        ) {
          SpeedLimitZone.PEDESTRIAN_SIDEWALK
        } else {
          SpeedLimitZone.BIKE_LANE
        }
      state.copy(
        selectedRouteId = routeId,
        activeSpeedZone = zone,
        pddAlerts = buildPddAlerts(state.rideMode, zone, state.currentSpeedKmh),
        fitRouteBoundsNonce = state.fitRouteBoundsNonce + 1,
      )
    }
  }

  fun recalculateRoutes() {
    val destination = _uiState.value.destinationPoint ?: return
    val origin = _uiState.value.originPoint
    val mode = _uiState.value.rideMode

    routingJob?.cancel()
    routingJob =
      viewModelScope.launch {
        _uiState.update { it.copy(isRouting = true, routingError = null) }
        routingRepository
          .buildScooterRoutes(origin, destination, mode)
          .onSuccess { bundle ->
            val first = bundle.options.firstOrNull()
            val zone =
              if (first?.segments?.firstOrNull()?.kind == RouteSegmentKind.PEDESTRIAN_DISMOUNT ||
                first?.segments?.firstOrNull()?.kind == RouteSegmentKind.SIDEWALK_SHORTCUT
              ) {
                SpeedLimitZone.PEDESTRIAN_SIDEWALK
              } else {
                SpeedLimitZone.BIKE_LANE
              }
            _uiState.update { state ->
              state.copy(
                routes = bundle.options,
                selectedRouteId = first?.id,
                roadHazards = bundle.hazards,
                isRouting = false,
                routingError = if (bundle.options.isEmpty()) "Маршруты не найдены" else null,
                activeSpeedZone = zone,
                pddAlerts = buildPddAlerts(mode, zone, state.currentSpeedKmh),
                fitRouteBoundsNonce = state.fitRouteBoundsNonce + 1,
              )
            }
          }
          .onFailure { err ->
            _uiState.update {
              it.copy(
                isRouting = false,
                routingError = err.message ?: "Ошибка построения маршрута",
              )
            }
          }
      }
  }

  fun onToggleNavigation() {
    _uiState.update { state ->
      val next = !state.isNavigating
      state.copy(
        isNavigating = next,
        cameraTarget = if (next) state.originPoint else state.cameraTarget,
        cameraActionNonce = if (next) state.cameraActionNonce + 1 else state.cameraActionNonce,
      )
    }
  }

  fun onClearDestination() {
    routingJob?.cancel()
    _uiState.update {
      it.copy(
        searchQuery = "",
        suggestions = emptyList(),
        isSearchFocused = false,
        destinationPoint = null,
        destinationTitle = null,
        routes = emptyList(),
        selectedRouteId = null,
        roadHazards = emptyList(),
        isRouting = false,
        routingError = null,
        isNavigating = false,
        activeSpeedZone = SpeedLimitZone.BIKE_LANE,
        pddAlerts = buildPddAlerts(it.rideMode, SpeedLimitZone.BIKE_LANE),
      )
    }
  }

  fun onCenterMyLocation() {
    _uiState.update {
      it.copy(
        cameraTarget = it.originPoint,
        cameraZoomDelta = null,
        cameraActionNonce = it.cameraActionNonce + 1,
      )
    }
  }

  fun onZoomIn() {
    _uiState.update {
      it.copy(
        cameraTarget = null,
        cameraZoomDelta = 1.0f,
        cameraActionNonce = it.cameraActionNonce + 1,
      )
    }
  }

  fun onZoomOut() {
    _uiState.update {
      it.copy(
        cameraTarget = null,
        cameraZoomDelta = -1.0f,
        cameraActionNonce = it.cameraActionNonce + 1,
      )
    }
  }

  fun onToggleTraffic(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setTrafficEnabled(enabled) }
  }

  fun onToggleRoadEvents(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setRoadEventsEnabled(enabled) }
  }

  fun onThemeSelected(theme: ThemePreference) {
    viewModelScope.launch { preferencesRepository.setThemePreference(theme) }
  }

  fun onMapTilesLoaded() {
    if (!_uiState.value.mapTilesLoaded) {
      _uiState.update { it.copy(mapTilesLoaded = true) }
    }
  }

  private fun inferSpeedZone(state: NavigatorUiState): SpeedLimitZone {
    val selected = state.selectedRoute ?: return SpeedLimitZone.BIKE_LANE
    val hasOnlyPedestrian =
      selected.segments.all {
        it.kind == RouteSegmentKind.PEDESTRIAN_DISMOUNT ||
          it.kind == RouteSegmentKind.SIDEWALK_SHORTCUT
      }
    return if (hasOnlyPedestrian) SpeedLimitZone.PEDESTRIAN_SIDEWALK else SpeedLimitZone.BIKE_LANE
  }

  private fun buildPddAlerts(
    rideMode: RideMode,
    zone: SpeedLimitZone,
    currentSpeedKmh: Int = 0,
  ): List<String> {
    val alerts = mutableListOf<String>()
    if (currentSpeedKmh > zone.maxSpeedKmh) {
      alerts.add("Превышение ${zone.maxSpeedKmh} км/ч по ПДД РК — снизьте скорость")
    }
    when (rideMode) {
      RideMode.FULL_LEGAL -> {
        alerts.add("До 18 лет: выезд на проезжую часть запрещён")
        alerts.add("На пешеходных переходах обязательно спешивайтесь")
      }
      RideMode.SEMI_LEGAL -> {
        alerts.add("На тротуарах скорость не выше 6 км/ч · уступайте пешеходам")
        alerts.add("При выезде на край ПЧ (18+): обязательны шлем и права")
      }
    }
    return alerts
  }
}
