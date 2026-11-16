package com.example.model

import com.yandex.mapkit.geometry.Point

/**
 * Режим передвижения на электросамокате согласно правилам ПДД РК.
 */
enum class RideMode {
  /**
   * «Фулл Легал» (для лиц до 18 лет и строгого соблюдения ПДД РК):
   * Только официальные велодорожки; при их разрыве — спешивание и переход по пешеходному маршруту (6 км/ч).
   * Выезд на проезжую часть полностью исключён.
   */
  FULL_LEGAL,

  /**
   * «Полулегальный» (шорткаты):
   * Комбинированный велосипедно-пешеходный граф, позволяющий связывать велодорожки
   * через тротуары и пешеходные зоны (лимит 6 км/ч на тротуарах), избегая длинных объездов.
   */
  SEMI_LEGAL,
}

enum class ThemePreference {
  SYSTEM,
  DAY,
  NIGHT,
}

enum class SpeedLimitZone(val maxSpeedKmh: Int, val label: String) {
  BIKE_LANE(25, "Велодорожка · макс. 25 км/ч"),
  PEDESTRIAN_SIDEWALK(6, "Тротуар / спешиться · макс. 6 км/ч"),
}

enum class RouteSegmentKind {
  BIKE_LANE,
  PEDESTRIAN_DISMOUNT,
  SIDEWALK_SHORTCUT,
  HAZARD_DETOUR,
}

data class RouteSegmentData(
  val kind: RouteSegmentKind,
  val points: List<Point>,
  val distanceMeters: Double,
  val durationSeconds: Double,
  val instruction: String? = null,
)

data class ElevationPointData(
  val distanceMeters: Double,
  val elevationMeters: Double,
  val gradePercent: Double,
)

data class ElevationProfile(
  val samples: List<ElevationPointData>,
  val totalAscentMeters: Int,
  val totalDescentMeters: Int,
  val minElevationMeters: Int,
  val maxElevationMeters: Int,
  val maxGradePercent: Double,
)

enum class RoadHazardType(val title: String) {
  ACCIDENT("ДТП на участке"),
  ROAD_CLOSURE("Перекрытие дороги"),
  ROAD_WORKS("Дорожные работы"),
  HEAVY_TRAFFIC("Затор / опасный трафик"),
}

data class RoadHazard(
  val id: String,
  val type: RoadHazardType,
  val description: String,
  val point: Point,
  val severity: Int, // 1..3
)

data class ScooterRouteOption(
  val id: String,
  val title: String,
  val badge: String,
  val rideMode: RideMode,
  val totalDistanceMeters: Double,
  val etaSeconds: Long,
  val bikeLanePercentage: Int,
  val dismountCount: Int,
  val segments: List<RouteSegmentData>,
  val allPoints: List<Point>,
  val elevationProfile: ElevationProfile,
  val hazardsOnRoute: List<RoadHazard>,
  val isDetourRoute: Boolean = false,
  val pddSummary: String,
)

data class SearchSuggestion(
  val id: String,
  val title: String,
  val subtitle: String,
  val point: Point?,
  val searchUri: String? = null,
  val distanceText: String? = null,
)

data class MapLayersState(
  val trafficEnabled: Boolean = true,
  val roadEventsEnabled: Boolean = true,
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
)

data class NavigatorUiState(
  val searchQuery: String = "",
  val isSearchFocused: Boolean = false,
  val suggestions: List<SearchSuggestion> = emptyList(),
  val isSearching: Boolean = false,
  val originPoint: Point = ALMATY_CENTER,
  val isUsingRealGps: Boolean = false,
  val locationPermissionGranted: Boolean = false,
  val destinationPoint: Point? = null,
  val destinationTitle: String? = null,
  val rideMode: RideMode = RideMode.FULL_LEGAL,
  val routes: List<ScooterRouteOption> = emptyList(),
  val selectedRouteId: String? = null,
  val isRouting: Boolean = false,
  val routingError: String? = null,
  val layers: MapLayersState = MapLayersState(),
  val roadHazards: List<RoadHazard> = emptyList(),
  val activeSpeedZone: SpeedLimitZone = SpeedLimitZone.BIKE_LANE,
  val isNavigating: Boolean = false,
  val currentSpeedKmh: Int = 0,
  val pddAlerts: List<String> = emptyList(),
  val isMapKitKeyConfigured: Boolean = true,
  val cameraActionNonce: Long = 0L,
  val cameraTarget: Point? = null,
  val cameraZoomDelta: Float? = null,
  val fitRouteBoundsNonce: Long = 0L,
) {
  val selectedRoute: ScooterRouteOption?
    get() = routes.firstOrNull { it.id == selectedRouteId } ?: routes.firstOrNull()

  companion object {
    // Центр Алматы (площадь Республики / пр. Абая — сеть велодорожек)
    val ALMATY_CENTER = Point(43.238949, 76.889709)
  }
}
