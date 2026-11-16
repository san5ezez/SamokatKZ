package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.NavigatorUiState
import com.example.model.SearchSuggestion
import com.example.ui.components.LayersDialog
import com.example.ui.components.RightMapControls
import com.example.ui.components.RouteBottomSheetContent
import com.example.ui.components.TopHudOverlay
import com.example.ui.components.YandexMapView
import com.example.viewmodel.NavigatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatorScreen(
  uiState: NavigatorUiState,
  isDarkTheme: Boolean,
  viewModel: NavigatorViewModel,
) {
  val context = LocalContext.current
  var showLayersDialog by remember { mutableStateOf(false) }

  val sheetState =
    rememberStandardBottomSheetState(
      initialValue = SheetValue.PartiallyExpanded,
      skipHiddenState = true,
    )
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
      val granted =
        results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      viewModel.onLocationPermissionUpdated(granted)
    }

  LaunchedEffect(Unit) {
    val fineGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarseGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fineGranted || coarseGranted) {
      viewModel.onLocationPermissionUpdated(true)
    } else {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      )
    }
  }

  // Автоматически раскрываем панель, когда появились варианты маршрута
  LaunchedEffect(uiState.routes.size) {
    if (uiState.routes.isNotEmpty()) {
      sheetState.expand()
    }
  }

  BackHandler(enabled = uiState.isSearchFocused || uiState.destinationPoint != null) {
    if (uiState.isSearchFocused) {
      viewModel.onSearchFocusChanged(false)
    } else if (uiState.destinationPoint != null) {
      viewModel.onClearDestination()
    }
  }

  BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetPeekHeight = 164.dp,
    sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    sheetContainerColor = MaterialTheme.colorScheme.surface,
    sheetContentColor = MaterialTheme.colorScheme.onSurface,
    sheetShadowElevation = 12.dp,
    sheetContent = {
      RouteBottomSheetContent(
        uiState = uiState,
        onRideModeSelected = viewModel::onRideModeSelected,
        onRouteSelected = viewModel::onRouteOptionSelected,
        onToggleNavigation = viewModel::onToggleNavigation,
        onRetryRouting = viewModel::recalculateRoutes,
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      // 1. Полноэкранная Яндекс Карта
      YandexMapView(
        uiState = uiState,
        isDarkMap = isDarkTheme,
        onMapLongTap = viewModel::onMapLongTap,
        onVisibleRegionChanged = viewModel::updateVisibleRegion,
        onRouteTapped = viewModel::onRouteOptionSelected,
        onMapTilesLoaded = viewModel::onMapTilesLoaded,
        modifier = Modifier.fillMaxSize(),
      )

      // 2. Полупрозрачный поиск + плашка 25/6 км/ч ПДД РК сверху
      TopHudOverlay(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchFocusChanged = viewModel::onSearchFocusChanged,
        onSuggestionSelected = { suggestion: SearchSuggestion ->
          viewModel.onSuggestionSelected(suggestion)
        },
        onQuickCategorySelected = viewModel::onQuickCategorySelected,
        onClearSearch = viewModel::onClearDestination,
        modifier = Modifier.align(Alignment.TopCenter),
      )

      // 3. Плавающие кнопки справа (Слои, Зум, Моё местоположение)
      RightMapControls(
        isUsingRealGps = uiState.isUsingRealGps,
        trafficEnabled = uiState.layers.trafficEnabled,
        onOpenLayersMenu = { showLayersDialog = true },
        onZoomIn = viewModel::onZoomIn,
        onZoomOut = viewModel::onZoomOut,
        onCenterMyLocation = {
          if (!uiState.locationPermissionGranted) {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
              )
            )
          }
          viewModel.onCenterMyLocation()
        },
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = innerPadding.calculateBottomPadding() + 20.dp),
      )
    }
  }

  if (showLayersDialog) {
    LayersDialog(
      layers = uiState.layers,
      onToggleTraffic = viewModel::onToggleTraffic,
      onToggleRoadEvents = viewModel::onToggleRoadEvents,
      onThemeSelected = viewModel::onThemeSelected,
      onDismiss = { showLayersDialog = false },
    )
  }
}
