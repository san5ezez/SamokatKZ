package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MapLayersState
import com.example.model.ThemePreference
import com.example.ui.theme.BikeLaneEmerald
import com.example.ui.theme.HazardCrimson

@Composable
fun LayersDialog(
  layers: MapLayersState,
  onToggleTraffic: (Boolean) -> Unit,
  onToggleRoadEvents: (Boolean) -> Unit,
  onThemeSelected: (ThemePreference) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.layers_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LayerSwitchRow(
          icon = Icons.Rounded.Traffic,
          iconTint = BikeLaneEmerald,
          title = stringResource(R.string.layer_traffic),
          subtitle = "Загруженность улиц в реальном времени",
          checked = layers.trafficEnabled,
          onCheckedChange = onToggleTraffic,
          modifier = Modifier.testTag("switch_traffic_layer"),
        )

        LayerSwitchRow(
          icon = Icons.Rounded.ReportProblem,
          iconTint = HazardCrimson,
          title = stringResource(R.string.layer_road_events),
          subtitle = "ДТП, перекрытия и дорожные работы",
          checked = layers.roadEventsEnabled,
          onCheckedChange = onToggleRoadEvents,
          modifier = Modifier.testTag("switch_road_events"),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Оформление карты (День / Ночь)",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ThemeOptionButton(
            selected = layers.themePreference == ThemePreference.SYSTEM,
            icon = Icons.Rounded.BrightnessAuto,
            label = stringResource(R.string.theme_system),
            onClick = { onThemeSelected(ThemePreference.SYSTEM) },
            modifier = Modifier.weight(1f).testTag("theme_system_button"),
          )
          ThemeOptionButton(
            selected = layers.themePreference == ThemePreference.DAY,
            icon = Icons.Rounded.LightMode,
            label = stringResource(R.string.theme_day),
            onClick = { onThemeSelected(ThemePreference.DAY) },
            modifier = Modifier.weight(1f).testTag("theme_day_button"),
          )
          ThemeOptionButton(
            selected = layers.themePreference == ThemePreference.NIGHT,
            icon = Icons.Rounded.DarkMode,
            label = stringResource(R.string.theme_night),
            onClick = { onThemeSelected(ThemePreference.NIGHT) },
            modifier = Modifier.weight(1f).testTag("theme_night_button"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Готово")
      }
    },
  )
}

@Composable
private fun LayerSwitchRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable { onCheckedChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
      modifier = Modifier.size(22.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun ThemeOptionButton(
  selected: Boolean,
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.minimumInteractiveComponentSize().clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color =
      if (selected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          if (selected) {
            MaterialTheme.colorScheme.onPrimary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color =
          if (selected) {
            MaterialTheme.colorScheme.onPrimary
          } else {
            MaterialTheme.colorScheme.onSurface
          },
      )
    }
  }
}
