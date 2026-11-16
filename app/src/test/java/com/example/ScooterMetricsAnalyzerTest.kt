package com.example

import com.example.data.ScooterMetricsAnalyzer
import com.example.model.RoadHazard
import com.example.model.RoadHazardType
import com.example.model.RouteSegmentData
import com.example.model.RouteSegmentKind
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScooterMetricsAnalyzerTest {

  @Test
  fun etaCalculationRespects25And6KmhLimits() {
    val bikeSegment =
      RouteSegmentData(
        kind = RouteSegmentKind.BIKE_LANE,
        points = emptyList(),
        distanceMeters = 2200.0,
        durationSeconds =
          ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(RouteSegmentKind.BIKE_LANE, 2200.0),
      )
    val sidewalkSegment =
      RouteSegmentData(
        kind = RouteSegmentKind.SIDEWALK_SHORTCUT,
        points = emptyList(),
        distanceMeters = 600.0,
        durationSeconds =
          ScooterMetricsAnalyzer.calculateSegmentEtaSeconds(
            RouteSegmentKind.SIDEWALK_SHORTCUT,
            600.0,
          ),
      )

    // 2200m at ~22 km/h = 360s; 600m at 6 km/h = 360s; total = 720s
    val totalEta = ScooterMetricsAnalyzer.calculateTotalEtaSeconds(listOf(bikeSegment, sidewalkSegment))
    assertEquals(720L, totalEta)
  }

  @Test
  fun hazardDetectionFindsNearbyAccidents() {
    val route =
      listOf(
        Point(43.2389, 76.8897),
        Point(43.2410, 76.8920),
        Point(43.2450, 76.8950),
      )
    val nearbyAccident =
      RoadHazard(
        id = "h1",
        type = RoadHazardType.ACCIDENT,
        description = "ДТП на перекрёстке",
        point = Point(43.2412, 76.8922),
        severity = 2,
      )
    val farClosure =
      RoadHazard(
        id = "h2",
        type = RoadHazardType.ROAD_CLOSURE,
        description = "Перекрытие в другом районе",
        point = Point(43.2900, 76.9500),
        severity = 3,
      )

    val found = ScooterMetricsAnalyzer.findHazardsNearRoute(route, listOf(nearbyAccident, farClosure))
    assertEquals(1, found.size)
    assertEquals("h1", found.first().id)
  }

  @Test
  fun elevationProfileGeneratesValidAscentAndDescent() {
    val points =
      listOf(
        Point(43.2500, 76.8897),
        Point(43.2389, 76.8897),
        Point(43.2280, 76.8897),
      )
    val profile = ScooterMetricsAnalyzer.buildElevationProfile(emptyList(), points, 2500.0)
    assertTrue(profile.samples.size >= 10)
    assertTrue(profile.totalAscentMeters > 0)
  }
}
