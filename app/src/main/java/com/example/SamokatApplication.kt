package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import com.yandex.mapkit.MapKitFactory

class SamokatApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // Skip native MapKit initialization under Robolectric JVM unit tests
    if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) {
      isMapKitConfigured = false
      return
    }

    val apiKey = BuildConfig.YANDEX_MAPKIT_API_KEY
    val hasRealKey = apiKey.isNotBlank() && apiKey != "YOUR_YANDEX_MAPKIT_API_KEY"
    runCatching {
      if (apiKey.isNotBlank()) {
        MapKitFactory.setApiKey(apiKey)
        MapKitFactory.setLocale("ru_RU")
        MapKitFactory.initialize(this)
      }
      isMapKitConfigured = hasRealKey
    }.onFailure { err ->
      Log.w(TAG, "MapKit initialization warning: ${err.message}")
      isMapKitConfigured = false
    }
  }

  companion object {
    private const val TAG = "SamokatApplication"
    @Volatile
    var isMapKitConfigured: Boolean = false
      private set
  }
}
