package com.example.data

import com.example.model.ElevationPointData
import com.example.model.ElevationProfile
import com.example.model.RoadHazard
import com.example.model.RouteSegmentData
import com.example.model.RouteSegmentKind
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.masstransit.ElevationData
import com.yandex.mapkit.transport.masstransit.InclineType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Расчёт ETA по ограничениям ПДД РК (25 км/ч велодорожка, 6 км/ч тротуар/спешивание),
 * анализ перепада высот (ElevationData) и пересечений с опасными участками/ДТП.
 */
object ScooterMetricsAnalyzer {

  // Крейсерская средняя скорость самоката с учётом лимита 25 км/ч и светофоров (~21.5 км/ч реальных, макс 25)
  private const val BIKE_LANE_EFFECTIVE_SPEED_MS = 22.0 / 3.6
  // ПДД РК: на тротуарах и пешеходных зонах максимум 6 км/ч
  private const val SIDEWALK_MAX_SPEED_MS = 6.0 / 3.6
  // Спешивание (ведение самоката рядом с собой)
  private const val DISMOUNT_WALK_SPEED_MS = 4.8 / 3.6

  fun calculateSegmentEtaSeconds(
    kind: RouteSegmentKind,
    distanceMeters: Double,
    elevationGradePenalty: Double = 1.0,
  ): Double {
    val baseSpeedMs =
      when (kind) {
        RouteSegmentKind.BIKE_LANE,
        RouteSegmentKind.HAZARD_DETOUR -> BIKE_LANE_EFFECTIVE_SPEED_MS
        RouteSegmentKind.SIDEWALK_SHORTCUT -> SIDEWALK_MAX_SPEED_MS
        RouteSegmentKind.PEDESTRIAN_DISMOUNT -> DISMOUNT_WALK_SPEED_MS
      }
    val adjustedSpeed = (baseSpeedMs / elevationGradePenalty.coerceIn(0.8, 1.35))
    return (distanceMeters / adjustedSpeed).coerceAtLeast(1.0)
  }

  fun calculateTotalEtaSeconds(segments: List<RouteSegmentData>): Long {
    val total = segments.sumOf { it.durationSeconds }
    return total.roundToInt().toLong().coerceAtLeast(60L)
  }

  fun buildElevationProfile(
    sdkElevationBlocks: List<ElevationData>,
    allPoints: List<Point>,
    totalDistanceMeters: Double,
  ): ElevationProfile {
    val sdkElevations = sdkElevationBlocks.flatMap { it.elevationSegments }.map { it.elevation.toDouble() }
    val sdkAscent = sdkElevationBlocks.sumOf { it.totalAscent?.value?.toInt() ?: 0 }
    val sdkDescent = sdkElevationBlocks.sumOf { it.totalDescent?.value?.toInt() ?: 0 }

    val samples: List<ElevationPointData> =
      if (sdkElevations.size >= 3) {
        val count = sdkElevations.size
        val stepDist = if (count > 1) totalDistanceMeters / (count - 1) else 0.0
        sdkElevations.mapIndexed { idx, elev ->
          val prevElev = if (idx > 0) sdkElevations[idx - 1] else elev
          val grade =
            if (idx > 0 && stepDist > 1.0) ((elev - prevElev) / stepDist) * 100.0 else 0.0
          ElevationPointData(
            distanceMeters = idx * stepDist,
            elevationMeters = elev,
            gradePercent = grade,
          )
        }
      } else {
        // Fallback: детерминированный профиль рельефа по координатам и наклонам секций
        // (в Алматы естественный уклон с юга на север: ~18 м на 1 км вверх к горам)
        buildAlmatyTerrainProfile(
          points = allPoints,
          totalDistanceMeters = totalDistanceMeters,
          inclineSections = sdkElevationBlocks.flatMap { it.inclineSections },
        )
      }

    var computedAscent = 0.0
    var computedDescent = 0.0
    var maxGrade = 0.0
    for (i in 1 until samples.size) {
      val diff = samples[i].elevationMeters - samples[i - 1].elevationMeters
      if (diff > 0) computedAscent += diff else computedDescent += abs(diff)
      maxGrade = maxOf(maxGrade, abs(samples[i].gradePercent))
    }

    val totalAscent = if (sdkAscent > 0) sdkAscent else computedAscent.roundToInt()
    val totalDescent = if (sdkDescent > 0) sdkDescent else computedDescent.roundToInt()
    val minElev = samples.minOfOrNull { it.elevationMeters }?.roundToInt() ?: 780
    val maxElev = samples.maxOfOrNull { it.elevationMeters }?.roundToInt() ?: 790

    return ElevationProfile(
      samples = samples,
      totalAscentMeters = totalAscent,
      totalDescentMeters = totalDescent,
      minElevationMeters = minElev,
      maxElevationMeters = maxElev,
      maxGradePercent = (maxGrade * 10.0).roundToInt() / 10.0,
    )
  }

  private fun buildAlmatyTerrainProfile(
    points: List<Point>,
    totalDistanceMeters: Double,
    inclineSections: List<com.yandex.mapkit.transport.masstransit.InclineSection>,
  ): List<ElevationPointData> {
    if (points.size < 2 || totalDistanceMeters <= 1.0) {
      return listOf(
        ElevationPointData(0.0, 785.0, 0.0),
        ElevationPointData(totalDistanceMeters.coerceAtLeast(100.0), 785.0, 0.0),
      )
    }

    val sampleCount = 24
    val result = ArrayList<ElevationPointData>(sampleCount)
    var cumulativeDist = 0.0
    val step = (points.size - 1).toDouble() / (sampleCount - 1)

    val inclineBias =
      inclineSections.sumOf {
        when (it.type) {
          InclineType.EXTREME_ASCENT -> 2.5
          InclineType.MODERATE_ASCENT -> 1.2
          InclineType.MODERATE_DESCENT -> -1.2
          InclineType.EXTREME_DESCENT -> -2.5
          else -> 0.0
        }
      }

    var prevPoint = points.first()
    var prevElev = estimateAlmatyElevationMeters(prevPoint)

    for (i in 0 until sampleCount) {
      val idx = (i * step).roundToInt().coerceIn(0, points.lastIndex)
      val pt = points[idx]
      if (i > 0) {
        cumulativeDist += haversineMeters(prevPoint, pt)
      }
      val baseElev = estimateAlmatyElevationMeters(pt)
      val biasOffset = (inclineBias * (i.toDouble() / sampleCount))
      val elev = baseElev + biasOffset
      val segDist = if (i > 0) haversineMeters(prevPoint, pt).coerceAtLeast(1.0) else 1.0
      val grade = if (i > 0) ((elev - prevElev) / segDist) * 100.0 else 0.0
      result.add(
        ElevationPointData(
          distanceMeters = (i.toDouble() / (sampleCount - 1)) * totalDistanceMeters,
          elevationMeters = elev,
          gradePercent = grade.coerceIn(-15.0, 15.0),
        )
      )
      prevPoint = pt
      prevElev = elev
    }
    return result
  }

  private fun estimateAlmatyElevationMeters(point: Point): Double {
    // Центр Алматы (~43.2389) ~790 м над уровнем моря, рост к югу (~220 м на 0.1° широты)
    val latOffset = 43.238949 - point.latitude
    val lonRipple = sin(point.longitude * 140.0) * 3.5 + cos(point.latitude * 180.0) * 2.5
    return 790.0 + (latOffset * 2100.0) + lonRipple
  }

  fun findHazardsNearRoute(
    routePoints: List<Point>,
    hazards: List<RoadHazard>,
    thresholdMeters: Double = 90.0,
  ): List<RoadHazard> {
    if (routePoints.isEmpty() || hazards.isEmpty()) return emptyList()
    val step = (routePoints.size / 40).coerceAtLeast(1)
    val sampledPoints = routePoints.filterIndexed { index, _ -> index % step == 0 }
    return hazards.filter { hazard ->
      sampledPoints.any { pt -> haversineMeters(pt, hazard.point) <= thresholdMeters }
    }
  }

  fun haversineMeters(a: Point, b: Point): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
  }
}
