package com.example.data

import com.example.model.RideMode
import com.example.model.RoadHazard
import com.example.model.RoadHazardType
import com.example.model.RouteSegmentData
import com.example.model.RouteSegmentKind
import com.example.model.ScooterRouteOption
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.SubpolylineHelper
import com.yandex.mapkit.navigation.JamType
import com.yandex.mapkit.road_events.EventTag
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.ElevationData
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.Route
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.mapkit.transport.masstransit.TrafficTypeID
import com.yandex.runtime.Error
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RoutingBundle(
  val options: List<ScooterRouteOption>,
  val hazards: List<RoadHazard>,
)

class ScooterRoutingRepository {

  private val scooterRouter by lazy { TransportFactory.getInstance().createScooterRouter() }
  private val bicycleRouterV2 by lazy { TransportFactory.getInstance().createBicycleRouterV2() }
  private val pedestrianRouter by lazy { TransportFactory.getInstance().createPedestrianRouter() }
  private val drivingRouter by lazy {
    DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
  }

  suspend fun buildScooterRoutes(
    origin: Point,
    destination: Point,
    rideMode: RideMode,
  ): Result<RoutingBundle> = coroutineScope {
    val points =
      listOf(
        RequestPoint(origin, RequestPointType.WAYPOINT, null, null, null),
        RequestPoint(destination, RequestPointType.WAYPOINT, null, null, null),
      )

    val scooterDeferred = async { requestBicycleV2(scooterRouter, points, avoidSteep = false) }
    val bikeFlatDeferred = async { requestBicycleV2(bicycleRouterV2, points, avoidSteep = true) }
    val pedestrianDeferred = async { requestPedestrian(points) }
    val drivingDeferred = async { requestDrivingForHazards(points) }

    val scooterRoutes = scooterDeferred.await()
    val flatBikeRoutes = bikeFlatDeferred.await()
    val pedestrianRoutes = pedestrianDeferred.await()
    val drivingRoutes = drivingDeferred.await()

    val hazards = extractRoadHazards(drivingRoutes)

    if (scooterRoutes.isEmpty() && flatBikeRoutes.isEmpty() && pedestrianRoutes.isEmpty()) {
      return@coroutineScope Result.failure(
        IllegalStateException("Не удалось построить маршрут. Проверьте подключение и API-ключ MapKit.")
      )
    }

    val candidates = mutableListOf<ScooterRouteOption>()

    when (rideMode) {
      RideMode.FULL_LEGAL -> {
        // 1. Строгий маршрут: велодорожки + принудительное спешивание на разрывах (0% ПЧ)
        scooterRoutes.take(2).forEachIndexed { idx, route ->
          val converted =
            convertToFullLegalOption(
              id = "full_legal_bike_$idx",
              title = if (idx == 0) "Велодорожки · Фулл Легал" else "Альтернатива · Пологий уклон",
              badge = if (idx == 0) "Только вело + спешивание" else "Щадящий рельеф",
              route = route,
              hazards = hazards,
            )
          candidates.add(converted)
        }

        // 2. Пологий вариант от BicycleRouterV2 (избегаем крутых подъёмов)
        flatBikeRoutes.firstOrNull()?.let { flatRoute ->
          candidates.add(
            convertToFullLegalOption(
              id = "full_legal_flat",
              title = "Без крутых подъёмов",
              badge = "Экономия заряда",
              route = flatRoute,
              hazards = hazards,
            )
          )
        }

        // 3. Полностью пешеходный/тротуарный со спешиванием (максимальная безопасность до 18 лет)
        pedestrianRoutes.firstOrNull()?.let { pedRoute ->
          candidates.add(
            convertPedestrianAsDismountOption(
              id = "full_legal_ped",
              title = "Безопасный (спешивание)",
              badge = "0% проезжей части",
              route = pedRoute,
              hazards = hazards,
              rideMode = RideMode.FULL_LEGAL,
            )
          )
        }
      }

      RideMode.SEMI_LEGAL -> {
        // 1. Оптимальный самокатный + шорткаты через тротуары
        scooterRoutes.take(2).forEachIndexed { idx, route ->
          candidates.add(
            convertToSemiLegalOption(
              id = "semi_legal_scoot_$idx",
              title = if (idx == 0) "Быстрый со шорткатами" else "Через соседние улицы",
              badge = if (idx == 0) "Вело + тротуары (6 км/ч)" else "Комбинированный",
              route = route,
              hazards = hazards,
            )
          )
        }

        // 2. Срезанный маршрут через парковые / пешеходные зоны
        pedestrianRoutes.firstOrNull()?.let { pedRoute ->
          candidates.add(
            convertPedestrianAsShortcutOption(
              id = "semi_legal_shortcut",
              title = "Срез через тротуары и парки",
              badge = "Без долгих объездов",
              route = pedRoute,
              hazards = hazards,
            )
          )
        }

        // 3. Пологий вело-вариант
        flatBikeRoutes.firstOrNull()?.let { flatRoute ->
          candidates.add(
            convertToSemiLegalOption(
              id = "semi_legal_flat",
              title = "Плавный профиль высот",
              badge = "Минимум уклона",
              route = flatRoute,
              hazards = hazards,
            )
          )
        }
      }
    }

    // Если на лучшем маршруте обнаружены ДТП / перекрытия / жёсткая пробка, строим явный объезд
    val primary = candidates.firstOrNull()
    if (primary != null && primary.hazardsOnRoute.isNotEmpty()) {
      val detourRoute = requestHazardDetour(origin, destination, primary.hazardsOnRoute.first(), rideMode, hazards)
      if (detourRoute != null) {
        candidates.add(1.coerceAtMost(candidates.size), detourRoute)
      }
    }

    val deduplicated = deduplicateOptions(candidates).take(4)
    Result.success(RoutingBundle(options = deduplicated, hazards = hazards))
  }

  private suspend fun requestHazardDetour(
    origin: Point,
    destination: Point,
    hazard: RoadHazard,
    rideMode: RideMode,
    allHazards: List<RoadHazard>,
  ): ScooterRouteOption? {
    val viaPoint = computeOrthogonalDetourPoint(origin, destination, hazard.point)
    val detourPoints =
      listOf(
        RequestPoint(origin, RequestPointType.WAYPOINT, null, null, null),
        RequestPoint(viaPoint, RequestPointType.VIAPOINT, null, null, null),
        RequestPoint(destination, RequestPointType.WAYPOINT, null, null, null),
      )

    val detourRoutes = requestBicycleV2(scooterRouter, detourPoints, avoidSteep = false)
    val bestDetour = detourRoutes.firstOrNull() ?: return null

    val option =
      if (rideMode == RideMode.FULL_LEGAL) {
        convertToFullLegalOption(
          id = "hazard_detour",
          title = "Объезд опасного участка",
          badge = "Без ДТП и перекрытий",
          route = bestDetour,
          hazards = allHazards,
          isDetour = true,
        )
      } else {
        convertToSemiLegalOption(
          id = "hazard_detour",
          title = "Объезд опасного участка",
          badge = "Безопасный шорткат",
          route = bestDetour,
          hazards = allHazards,
          isDetour = true,
        )
      }
    return option
  }

  private fun computeOrthogonalDetourPoint(
    origin: Point,
    destination: Point,
    hazard: Point,
  ): Point {
    val midLat = (origin.latitude + destination.latitude) / 2.0
    val midLon = (origin.longitude + destination.longitude) / 2.0
    val dLat = destination.latitude - origin.latitude
    val dLon = destination.longitude - origin.longitude
    val norm = sqrt(dLat * dLat + dLon * dLon).coerceAtLeast(1e-6)
    // Перпендикулярный вектор смещения ~220 м
    val shift = 0.0022
    val perpLat = -dLon / norm * shift
    val perpLon = dLat / norm * shift
    val candidateA = Point(midLat + perpLat, midLon + perpLon)
    val candidateB = Point(midLat - perpLat, midLon - perpLon)
    return if (
      ScooterMetricsAnalyzer.haversineMeters(candidateA, hazard) >
        ScooterMetricsAnalyzer.haversineMeters(candidateB, hazard)
    ) {
      candidateA
    } else {
      candidateB
    }
  }

  private suspend fun convertToFullLegalOption(
    id: String,
    title: String,
    badge: String,
    route: Route,
    hazards: List<RoadHazard>,
    isDetour: Boolean = false,
  ): ScooterRouteOption {
    val geometry = route.geometry
    val segments = mutableListOf<RouteSegmentData>()
    val elevationBlocks = mutableListOf<ElevationData>()
    var bikeMeters = 0.0
    var totalMeters = 0.0
    var dismountCount = 0

    for (section in route.sections) {
      val fitness = section.metadata.data.fitness
      fitness?.elevationData?.let { elevationBlocks.add(it) }

      val trafficTypes = fitness?.trafficTypes.orEmpty()
      if (trafficTypes.isEmpty()) {
        val poly = SubpolylineHelper.subpolyline(geometry, section.geometry)
        val dist = SubpolylineHelper.subpolylineLength(geometry, section.geometry)
        totalMeters += dist
        val kind =
          if (isDetour) RouteSegmentKind.HAZARD_DETOUR else RouteSegmentKind.BIKE_LANE
        bikeMeters += dist
        segments.add(
          RouteSegmentData(
            kind = kind,
            points = poly.points,
            distanceMeters = dist,
            durationSeconds = ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(kind, dist),
          )
        )
      } else {
        for (tt in trafficTypes) {
          val subPoly = SubpolylineHelper.subpolyline(geometry, tt.subpolyline)
          val dist = SubpolylineHelper.subpolylineLength(geometry, tt.subpolyline)
          totalMeters += dist

          when (tt.trafficType) {
            TrafficTypeID.BICYCLE -> {
              bikeMeters += dist
              val kind =
                if (isDetour) RouteSegmentKind.HAZARD_DETOUR else RouteSegmentKind.BIKE_LANE
              segments.add(
                RouteSegmentData(
                  kind = kind,
                  points = subPoly.points,
                  distanceMeters = dist,
                  durationSeconds = ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(kind, dist),
                )
              )
            }

            TrafficTypeID.AUTO, TrafficTypeID.OTHER -> {
              // В режиме «Фулл Легал» проезжая часть строго запрещена:
              // перестраиваем разрыв на пешеходный обход со спешиванием
              dismountCount++
              val pts = subPoly.points
              val replacementPoints =
                if (pts.size >= 2 && dist > 80.0) {
                  requestPedestrianSegmentPoints(pts.first(), pts.last()) ?: pts
                } else {
                  pts
                }
              segments.add(
                RouteSegmentData(
                  kind = RouteSegmentKind.PEDESTRIAN_DISMOUNT,
                  points = replacementPoints,
                  distanceMeters = dist,
                  durationSeconds =
                    ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
                      RouteSegmentKind.PEDESTRIAN_DISMOUNT,
                      dist,
                    ),
                  instruction = "Разрыв велодорожки: спешиться и вести самокат рядом (6 км/ч)",
                )
              )
            }

            TrafficTypeID.PEDESTRIAN -> {
              dismountCount++
              segments.add(
                RouteSegmentData(
                  kind = RouteSegmentKind.PEDESTRIAN_DISMOUNT,
                  points = subPoly.points,
                  distanceMeters = dist,
                  durationSeconds =
                    ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
                      RouteSegmentKind.PEDESTRIAN_DISMOUNT,
                      dist,
                    ),
                  instruction = "Пешеходный участок: спешиться",
                )
              )
            }
          }
        }
      }
    }

    val allPoints = segments.flatMap { it.points }.ifEmpty { geometry.points }
    val finalDist = totalMeters.coerceAtLeast(10.0)
    val bikePct = ((bikeMeters / finalDist) * 100.0).roundToInt().coerceIn(0, 100)
    val etaSeconds = ScooterMetricsAnalyzer.calculateTotalEtaSeconds(segments)
    val elevationProfile =
      ScooterMetricsAnalyzer.buildElevationProfile(elevationBlocks, allPoints, finalDist)
    val routeHazards = ScooterMetricsAnalyzer.findHazardsNearRoute(allPoints, hazards)

    return ScooterRouteOption(
      id = id,
      title = title,
      badge = badge,
      rideMode = RideMode.FULL_LEGAL,
      totalDistanceMeters = finalDist,
      etaSeconds = etaSeconds,
      bikeLanePercentage = bikePct,
      dismountCount = dismountCount,
      segments = segments,
      allPoints = allPoints,
      elevationProfile = elevationProfile,
      hazardsOnRoute = routeHazards,
      isDetourRoute = isDetour,
      pddSummary =
        if (dismountCount > 0) {
          "0% ПЧ · $dismountCount уч. спешивания на разрывах велосети"
        } else {
          "100% официальная велоинфраструктура · до 25 км/ч"
        },
    )
  }

  private fun convertToSemiLegalOption(
    id: String,
    title: String,
    badge: String,
    route: Route,
    hazards: List<RoadHazard>,
    isDetour: Boolean = false,
  ): ScooterRouteOption {
    val geometry = route.geometry
    val segments = mutableListOf<RouteSegmentData>()
    val elevationBlocks = mutableListOf<ElevationData>()
    var bikeMeters = 0.0
    var totalMeters = 0.0
    var shortcutCount = 0

    for (section in route.sections) {
      val fitness = section.metadata.data.fitness
      fitness?.elevationData?.let { elevationBlocks.add(it) }

      val trafficTypes = fitness?.trafficTypes.orEmpty()
      if (trafficTypes.isEmpty()) {
        val poly = SubpolylineHelper.subpolyline(geometry, section.geometry)
        val dist = SubpolylineHelper.subpolylineLength(geometry, section.geometry)
        totalMeters += dist
        bikeMeters += dist
        val kind =
          if (isDetour) RouteSegmentKind.HAZARD_DETOUR else RouteSegmentKind.BIKE_LANE
        segments.add(
          RouteSegmentData(
            kind = kind,
            points = poly.points,
            distanceMeters = dist,
            durationSeconds = ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(kind, dist),
          )
        )
      } else {
        for (tt in trafficTypes) {
          val subPoly = SubpolylineHelper.subpolyline(geometry, tt.subpolyline)
          val dist = SubpolylineHelper.subpolylineLength(geometry, tt.subpolyline)
          totalMeters += dist

          when (tt.trafficType) {
            TrafficTypeID.BICYCLE -> {
              bikeMeters += dist
              val kind =
                if (isDetour) RouteSegmentKind.HAZARD_DETOUR else RouteSegmentKind.BIKE_LANE
              segments.add(
                RouteSegmentData(
                  kind = kind,
                  points = subPoly.points,
                  distanceMeters = dist,
                  durationSeconds = ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(kind, dist),
                )
              )
            }

            TrafficTypeID.PEDESTRIAN, TrafficTypeID.OTHER, TrafficTypeID.AUTO -> {
              shortcutCount++
              segments.add(
                RouteSegmentData(
                  kind = RouteSegmentKind.SIDEWALK_SHORTCUT,
                  points = subPoly.points,
                  distanceMeters = dist,
                  durationSeconds =
                    ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
                      RouteSegmentKind.SIDEWALK_SHORTCUT,
                      dist,
                    ),
                  instruction = "Связка через тротуар / пешеходную зону (макс. 6 км/ч)",
                )
              )
            }
          }
        }
      }
    }

    val allPoints = segments.flatMap { it.points }.ifEmpty { geometry.points }
    val finalDist = totalMeters.coerceAtLeast(10.0)
    val bikePct = ((bikeMeters / finalDist) * 100.0).roundToInt().coerceIn(0, 100)
    val etaSeconds = ScooterMetricsAnalyzer.calculateTotalEtaSeconds(segments)
    val elevationProfile =
      ScooterMetricsAnalyzer.buildElevationProfile(elevationBlocks, allPoints, finalDist)
    val routeHazards = ScooterMetricsAnalyzer.findHazardsNearRoute(allPoints, hazards)

    return ScooterRouteOption(
      id = id,
      title = title,
      badge = badge,
      rideMode = RideMode.SEMI_LEGAL,
      totalDistanceMeters = finalDist,
      etaSeconds = etaSeconds,
      bikeLanePercentage = bikePct,
      dismountCount = shortcutCount,
      segments = segments,
      allPoints = allPoints,
      elevationProfile = elevationProfile,
      hazardsOnRoute = routeHazards,
      isDetourRoute = isDetour,
      pddSummary = "Велополосы ($bikePct%) + $shortcutCount тротуарных шортката (6 км/ч)",
    )
  }

  private fun convertPedestrianAsDismountOption(
    id: String,
    title: String,
    badge: String,
    route: Route,
    hazards: List<RoadHazard>,
    rideMode: RideMode,
  ): ScooterRouteOption {
    val elevationBlocks =
      route.sections.mapNotNull { it.metadata.data.fitness?.elevationData }
    val points = route.geometry.points
    val dist = route.metadata.weight.walkingDistance.value.coerceAtLeast(50.0)
    val seg =
      RouteSegmentData(
        kind = RouteSegmentKind.PEDESTRIAN_DISMOUNT,
        points = points,
        distanceMeters = dist,
        durationSeconds =
          ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
            RouteSegmentKind.PEDESTRIAN_DISMOUNT,
            dist,
          ),
        instruction = "Движение по тротуару / спешивание на переходах",
      )
    val elevationProfile =
      ScooterMetricsAnalyzer.buildElevationProfile(elevationBlocks, points, dist)
    val routeHazards = ScooterMetricsAnalyzer.findHazardsNearRoute(points, hazards)

    return ScooterRouteOption(
      id = id,
      title = title,
      badge = badge,
      rideMode = rideMode,
      totalDistanceMeters = dist,
      etaSeconds = ScooterMetricsAnalyzer.calculateTotalEtaSeconds(listOf(seg)),
      bikeLanePercentage = 0,
      dismountCount = 1,
      segments = listOf(seg),
      allPoints = points,
      elevationProfile = elevationProfile,
      hazardsOnRoute = routeHazards,
      pddSummary = "Только тротуары и переходы (без проезжей части)",
    )
  }

  private fun convertPedestrianAsShortcutOption(
    id: String,
    title: String,
    badge: String,
    route: Route,
    hazards: List<RoadHazard>,
  ): ScooterRouteOption {
    val elevationBlocks =
      route.sections.mapNotNull { it.metadata.data.fitness?.elevationData }
    val points = route.geometry.points
    val dist = route.metadata.weight.walkingDistance.value.coerceAtLeast(50.0)
    val seg =
      RouteSegmentData(
        kind = RouteSegmentKind.SIDEWALK_SHORTCUT,
        points = points,
        distanceMeters = dist,
        durationSeconds =
          ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
            RouteSegmentKind.SIDEWALK_SHORTCUT,
            dist,
          ),
        instruction = "Прямой тротуарный шорткат (макс. 6 км/ч)",
      )
    val elevationProfile =
      ScooterMetricsAnalyzer.buildElevationProfile(elevationBlocks, points, dist)
    val routeHazards = ScooterMetricsAnalyzer.findHazardsNearRoute(points, hazards)

    return ScooterRouteOption(
      id = id,
      title = title,
      badge = badge,
      rideMode = RideMode.SEMI_LEGAL,
      totalDistanceMeters = dist,
      etaSeconds = ScooterMetricsAnalyzer.calculateTotalEtaSeconds(listOf(seg)),
      bikeLanePercentage = 0,
      dismountCount = 1,
      segments = listOf(seg),
      allPoints = points,
      elevationProfile = elevationProfile,
      hazardsOnRoute = routeHazards,
      pddSummary = "Короткая срезка через пешеходные зоны (лимит 6 км/ч)",
    )
  }

  private fun deduplicateOptions(options: List<ScooterRouteOption>): List<ScooterRouteOption> {
    val unique = mutableListOf<ScooterRouteOption>()
    for (opt in options) {
      val isDuplicate =
        unique.any { existing ->
          !opt.isDetourRoute &&
            !existing.isDetourRoute &&
            kotlin.math.abs(existing.totalDistanceMeters - opt.totalDistanceMeters) < 35.0 &&
            existing.bikeLanePercentage == opt.bikeLanePercentage
        }
      if (!isDuplicate) unique.add(opt)
    }
    return unique
  }

  private fun extractRoadHazards(drivingRoutes: List<DrivingRoute>): List<RoadHazard> {
    val hazards = mutableListOf<RoadHazard>()
    val seenIds = mutableSetOf<String>()

    for (route in drivingRoutes) {
      for (event in route.events) {
        if (!seenIds.add(event.eventId)) continue
        val tags = event.tags
        val hazardType =
          when {
            EventTag.CLOSED in tags -> RoadHazardType.ROAD_CLOSURE
            EventTag.ACCIDENT in tags -> RoadHazardType.ACCIDENT
            EventTag.RECONSTRUCTION in tags -> RoadHazardType.ROAD_WORKS
            EventTag.DANGER in tags -> RoadHazardType.ACCIDENT
            else -> null
          } ?: continue

        hazards.add(
          RoadHazard(
            id = event.eventId,
            type = hazardType,
            description = event.descriptionText?.takeIf { it.isNotBlank() } ?: hazardType.title,
            point = event.location,
            severity = if (hazardType == RoadHazardType.ROAD_CLOSURE) 3 else 2,
          )
        )
      }

      // Также фиксируем тяжёлые пробки (BLOCKED / VERY_HARD) вдоль коридора
      val geomPts = route.geometry.points
      val jams = route.jamSegments
      if (geomPts.isNotEmpty() && jams.isNotEmpty()) {
        jams.forEachIndexed { idx, jam ->
          if (jam.jamType == JamType.BLOCKED || jam.jamType == JamType.VERY_HARD) {
            val ptIdx =
              ((idx.toDouble() / jams.size.coerceAtLeast(1)) * (geomPts.size - 1))
                .roundToInt()
                .coerceIn(0, geomPts.lastIndex)
            val jamId = "jam_${route.routeId}_$idx"
            if (seenIds.add(jamId)) {
              val pt = geomPts[ptIdx]
              val tooCloseToExisting =
                hazards.any { ScooterMetricsAnalyzer.haversineMeters(it.point, pt) < 250.0 }
              if (!tooCloseToExisting) {
                hazards.add(
                  RoadHazard(
                    id = jamId,
                    type =
                      if (jam.jamType == JamType.BLOCKED) {
                        RoadHazardType.ROAD_CLOSURE
                      } else {
                        RoadHazardType.HEAVY_TRAFFIC
                      },
                    description =
                      if (jam.jamType == JamType.BLOCKED) {
                        "Движение перекрыто (Яндекс Пробки)"
                      } else {
                        "Сильный затор (${(jam.speed * 3.6).roundToInt()} км/ч)"
                      },
                    point = pt,
                    severity = if (jam.jamType == JamType.BLOCKED) 3 else 2,
                  )
                )
              }
            }
          }
        }
      }
    }
    return hazards
  }

  private suspend fun requestBicycleV2(
    router: com.yandex.mapkit.transport.masstransit.BicycleRouterV2,
    points: List<RequestPoint>,
    avoidSteep: Boolean,
  ): List<Route> =
    suspendCancellableCoroutine { cont ->
      val routeOptions = RouteOptions(FitnessOptions(avoidSteep, true))
      val session =
        router.requestRoutes(
          points,
          TimeOptions(),
          routeOptions,
          object : Session.RouteListener {
            override fun onMasstransitRoutes(routes: MutableList<Route>) {
              if (cont.isActive) cont.resume(routes.toList())
            }

            override fun onMasstransitRoutesError(error: Error) {
              if (cont.isActive) cont.resume(emptyList())
            }
          },
        )
      cont.invokeOnCancellation { session.cancel() }
    }

  private suspend fun requestPedestrian(points: List<RequestPoint>): List<Route> =
    suspendCancellableCoroutine { cont ->
      val routeOptions = RouteOptions(FitnessOptions(false, true))
      val session =
        pedestrianRouter.requestRoutes(
          points,
          TimeOptions(),
          routeOptions,
          object : Session.RouteListener {
            override fun onMasstransitRoutes(routes: MutableList<Route>) {
              if (cont.isActive) cont.resume(routes.toList())
            }

            override fun onMasstransitRoutesError(error: Error) {
              if (cont.isActive) cont.resume(emptyList())
            }
          },
        )
      cont.invokeOnCancellation { session.cancel() }
    }

  private suspend fun requestPedestrianSegmentPoints(from: Point, to: Point): List<Point>? {
    val segPts =
      listOf(
        RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
        RequestPoint(to, RequestPointType.WAYPOINT, null, null, null),
      )
    return requestPedestrian(segPts).firstOrNull()?.geometry?.points
  }

  private suspend fun requestDrivingForHazards(points: List<RequestPoint>): List<DrivingRoute> =
    suspendCancellableCoroutine { cont ->
      val options = DrivingOptions().setRoutesCount(3)
      val session: DrivingSession =
        drivingRouter.requestRoutes(
          points,
          options,
          VehicleOptions(),
          object : DrivingSession.DrivingRouteListener {
            override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
              if (cont.isActive) cont.resume(routes.toList())
            }

            override fun onDrivingRoutesError(error: Error) {
              if (cont.isActive) cont.resume(emptyList())
            }
          },
        )
      cont.invokeOnCancellation { session.cancel() }
    }
}
