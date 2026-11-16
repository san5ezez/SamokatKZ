package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GpsLocationReading(
  val point: Point,
  val speedKmh: Int,
)

class LocationRepository(context: Context) {

  private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

  @SuppressLint("MissingPermission")
  suspend fun getCurrentLocation(): GpsLocationReading? =
    suspendCancellableCoroutine { cont ->
      fusedClient.lastLocation
        .addOnSuccessListener { location ->
          if (location != null) {
            val speedKmh = (location.speed * 3.6f).toInt().coerceAtLeast(0)
            cont.resume(
              GpsLocationReading(
                point = Point(location.latitude, location.longitude),
                speedKmh = speedKmh,
              )
            )
          } else {
            cont.resume(null)
          }
        }
        .addOnFailureListener { cont.resume(null) }
    }

  @SuppressLint("MissingPermission")
  fun locationUpdatesFlow(): Flow<GpsLocationReading> = callbackFlow {
    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
        .setMinUpdateIntervalMillis(1_500L)
        .setMinUpdateDistanceMeters(3f)
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val loc = result.lastLocation ?: return
          val speedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
          trySend(
            GpsLocationReading(
              point = Point(loc.latitude, loc.longitude),
              speedKmh = speedKmh,
            )
          )
        }
      }

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose { fusedClient.removeLocationUpdates(callback) }
  }
}
