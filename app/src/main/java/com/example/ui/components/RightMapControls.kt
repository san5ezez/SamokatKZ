package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun RightMapControls(
  isUsingRealGps: Boolean,
  trafficEnabled: Boolean,
  onOpenLayersMenu: () -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onCenterMyLocation: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(end = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Кнопка слоёв (Пробки, ДТП, День/Ночь)
    SmallFloatingActionButton(
      onClick = onOpenLayersMenu,
      shape = CircleShape,
      containerColor =
        if (trafficEnabled) {
          MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
        },
      contentColor =
        if (trafficEnabled) {
          MaterialTheme.colorScheme.secondary
        } else {
          MaterialTheme.colorScheme.onSurface
        },
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp),
      modifier = Modifier.minimumInteractiveComponentSize().testTag("layers_fab"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Layers,
        contentDescription = stringResource(R.string.layers_cd),
      )
    }

    // Компактные кнопки масштаба (+/-)
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
      shadowElevation = 5.dp,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = onZoomIn,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("zoom_in_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.zoom_in_cd),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
        HorizontalDivider(
          modifier = Modifier.size(width = 28.dp, height = 1.dp),
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
        IconButton(
          onClick = onZoomOut,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("zoom_out_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Remove,
            contentDescription = stringResource(R.string.zoom_out_cd),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    // Кнопка «Моё местоположение»
    FloatingActionButton(
      onClick = onCenterMyLocation,
      shape = CircleShape,
      containerColor =
        if (isUsingRealGps) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
      contentColor =
        if (isUsingRealGps) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurface
        },
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
      modifier = Modifier.minimumInteractiveComponentSize().testTag("my_location_fab"),
    ) {
      Icon(
        imageVector = Icons.Rounded.MyLocation,
        contentDescription = stringResource(R.string.my_location_cd),
      )
    }
  }
}
