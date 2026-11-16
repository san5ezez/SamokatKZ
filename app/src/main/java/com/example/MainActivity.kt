package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ThemePreference
import com.example.ui.NavigatorScreen
import com.example.ui.theme.SamokatTheme
import com.example.viewmodel.NavigatorViewModel
import com.yandex.mapkit.MapKitFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: NavigatorViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val isDarkTheme =
        when (uiState.layers.themePreference) {
          ThemePreference.SYSTEM -> systemDark
          ThemePreference.DAY -> false
          ThemePreference.NIGHT -> true
        }

      SamokatTheme(darkTheme = isDarkTheme) {
        NavigatorScreen(
          uiState = uiState,
          isDarkTheme = isDarkTheme,
          viewModel = viewModel,
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    runCatching { MapKitFactory.getInstance().onStart() }
  }

  override fun onStop() {
    runCatching { MapKitFactory.getInstance().onStop() }
    super.onStop()
  }
}
