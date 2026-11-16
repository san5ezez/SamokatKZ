package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.MapLayersState
import com.example.model.RideMode
import com.example.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.navigatorDataStore by preferencesDataStore(name = "samokat_kz_prefs")

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val TRAFFIC_ENABLED = booleanPreferencesKey("traffic_enabled")
    val ROAD_EVENTS_ENABLED = booleanPreferencesKey("road_events_enabled")
    val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    val RIDE_MODE = stringPreferencesKey("ride_mode")
  }

  val layersFlow: Flow<MapLayersState> =
    context.navigatorDataStore.data.map { prefs ->
      val themeName = prefs[Keys.THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
      val theme =
        runCatching { ThemePreference.valueOf(themeName) }.getOrDefault(ThemePreference.SYSTEM)
      MapLayersState(
        trafficEnabled = prefs[Keys.TRAFFIC_ENABLED] ?: true,
        roadEventsEnabled = prefs[Keys.ROAD_EVENTS_ENABLED] ?: true,
        themePreference = theme,
      )
    }

  val rideModeFlow: Flow<RideMode> =
    context.navigatorDataStore.data.map { prefs ->
      val modeName = prefs[Keys.RIDE_MODE] ?: RideMode.FULL_LEGAL.name
      runCatching { RideMode.valueOf(modeName) }.getOrDefault(RideMode.FULL_LEGAL)
    }

  suspend fun setTrafficEnabled(enabled: Boolean) {
    context.navigatorDataStore.edit { it[Keys.TRAFFIC_ENABLED] = enabled }
  }

  suspend fun setRoadEventsEnabled(enabled: Boolean) {
    context.navigatorDataStore.edit { it[Keys.ROAD_EVENTS_ENABLED] = enabled }
  }

  suspend fun setThemePreference(theme: ThemePreference) {
    context.navigatorDataStore.edit { it[Keys.THEME_PREFERENCE] = theme.name }
  }

  suspend fun setRideMode(mode: RideMode) {
    context.navigatorDataStore.edit { it[Keys.RIDE_MODE] = mode.name }
  }
}
