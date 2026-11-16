package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.NavigatorUiState
import com.example.model.RideMode
import com.example.model.ScooterRouteOption
import com.example.ui.theme.BikeLaneEmerald
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PedestrianBlue
import com.example.ui.theme.ShortcutAmber
import kotlin.math.roundToInt

@Composable
fun RouteBottomSheetContent(
  uiState: NavigatorUiState,
  onRideModeSelected: (RideMode) -> Unit,
  onRouteSelected: (String) -> Unit,
  onToggleNavigation: () -> Unit,
  onRetryRouting: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 16.dp)
        .padding(bottom = 18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 1. Переключатель режимов езды по ПДД РК
    RideModeSwitcher(
      selectedMode = uiState.rideMode,
      onModeSelected = onRideModeSelected,
    )

    // Описание активного режима
    Text(
      text =
        if (uiState.rideMode == RideMode.FULL_LEGAL) {
          stringResource(R.string.mode_full_legal_desc)
        } else {
          stringResource(R.string.mode_semi_legal_desc)
        },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 2. Состояние маршрутизации
    when {
      uiState.isRouting -> {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              strokeWidth = 2.5.dp,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Строим варианты маршрута по велосети и тротуарам…",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      uiState.routingError != null -> {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = uiState.routingError,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryRouting) {
              Text("Повторить")
            }
          }
        }
      }

      uiState.routes.isNotEmpty() -> {
        val selected = uiState.selectedRoute

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.routes_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          uiState.destinationTitle?.let { destTitle ->
            Text(
              text = destTitle,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.width(170.dp),
            )
          }
        }

        // Несколько вариантов маршрута на выбор (горизонтальная лента как в Яндекс Картах)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
        ) {
          items(uiState.routes, key = { it.id }) { option ->
            RouteOptionCard(
              option = option,
              isSelected = option.id == selected?.id,
              onClick = { onRouteSelected(option.id) },
            )
          }
        }

        // Детали выбранного маршрута
        if (selected != null) {
          // Баннер предупреждения о ДТП / перекрытии и предложение объезда
          val detourOption = uiState.routes.firstOrNull { it.isDetourRoute && it.id != selected.id }
          AnimatedVisibility(visible = selected.hazardsOnRoute.isNotEmpty()) {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color = HazardCrimson.copy(alpha = 0.13f),
              border = BorderStroke(1.dp, HazardCrimson.copy(alpha = 0.4f)),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.WarningAmber,
                  contentDescription = null,
                  tint = HazardCrimson,
                )
                Column(modifier = Modifier.weight(1f)) {
                  val topHazard = selected.hazardsOnRoute.first()
                  Text(
                    text = "${topHazard.type.title} · ${selected.hazardsOnRoute.size} уч.",
                    style = MaterialTheme.typography.titleSmall,
                    color = HazardCrimson,
                  )
                  Text(
                    text = topHazard.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (detourOption != null) {
                  TextButton(onClick = { onRouteSelected(detourOption.id) }) {
                    Text("Объехать", color = HazardCrimson, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }

          // Сводка ПДД РК для выбранного маршрута
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = selected.pddSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          // График перепада высот
          ElevationProfileChart(profile = selected.elevationProfile)

          // Кнопка старта навигации
          Button(
            onClick = onToggleNavigation,
            modifier =
              Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_navigation_button"),
            shape = RoundedCornerShape(16.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  if (uiState.isNavigating) HazardCrimson else MaterialTheme.colorScheme.primary,
                contentColor =
                  if (uiState.isNavigating) Color.White else MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            Icon(
              imageVector =
                if (uiState.isNavigating) Icons.Rounded.StopCircle else Icons.Rounded.Navigation,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                if (uiState.isNavigating) {
                  stringResource(R.string.stop_navigation)
                } else {
                  "${stringResource(R.string.start_navigation)} · ${formatEta(selected.etaSeconds)}"
                },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      else -> {
        // Нулевое состояние до выбора точки
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.ElectricScooter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.empty_route_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              LegendDot(color = BikeLaneEmerald, label = "Велодорожка (25 км/ч)")
              LegendDot(color = PedestrianBlue, label = "Спешиться (6 км/ч)")
              LegendDot(color = ShortcutAmber, label = "Шорткат")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RideModeSwitcher(
  selectedMode: RideMode,
  onModeSelected: (RideMode) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      ModeTabItem(
        selected = selectedMode == RideMode.FULL_LEGAL,
        icon = Icons.Rounded.Shield,
        label = stringResource(R.string.mode_full_legal),
        accentColor = BikeLaneEmerald,
        onClick = { onModeSelected(RideMode.FULL_LEGAL) },
        modifier = Modifier.weight(1f).testTag("mode_full_legal"),
      )
      ModeTabItem(
        selected = selectedMode == RideMode.SEMI_LEGAL,
        icon = Icons.AutoMirrored.Rounded.AltRoute,
        label = stringResource(R.string.mode_semi_legal),
        accentColor = ShortcutAmber,
        onClick = { onModeSelected(RideMode.SEMI_LEGAL) },
        modifier = Modifier.weight(1f).testTag("mode_semi_legal"),
      )
    }
  }
}

@Composable
private fun ModeTabItem(
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.minimumInteractiveComponentSize().clickable(onClick = onClick),
    shape = RoundedCornerShape(11.dp),
    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
    shadowElevation = if (selected) 2.dp else 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color =
          if (selected) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun RouteOptionCard(
  option: ScooterRouteOption,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val borderColor =
    when {
      isSelected && option.isDetourRoute -> BikeLaneEmerald
      isSelected -> MaterialTheme.colorScheme.primary
      else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

  Surface(
    modifier =
      Modifier
        .width(205.dp)
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .testTag("route_card_${option.id}"),
    shape = RoundedCornerShape(16.dp),
    color =
      if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      },
    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = formatEta(option.etaSeconds),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Text(
        text = "${formatDistance(option.totalDistanceMeters)} · ${option.title}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.DirectionsBike,
            contentDescription = null,
            tint = BikeLaneEmerald,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "${option.bikeLanePercentage}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
            contentDescription = null,
            tint = PedestrianBlue,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "${option.dismountCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (option.hazardsOnRoute.isNotEmpty()) {
          Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = "Опасность на маршруте",
            tint = HazardCrimson,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color =
          if (option.isDetourRoute) {
            BikeLaneEmerald.copy(alpha = 0.16f)
          } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
          },
      ) {
        Text(
          text = option.badge,
          style = MaterialTheme.typography.labelSmall,
          color = if (option.isDetourRoute) BikeLaneEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}

private fun formatEta(seconds: Long): String {
  val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
  return if (minutes >= 60) {
    val h = minutes / 60
    val m = minutes % 60
    "$h ч $m мин"
  } else {
    "$minutes мин"
  }
}

private fun formatDistance(meters: Double): String {
  return if (meters >= 1000.0) {
    "${"%.1f".format(meters / 1000.0)} км"
  } else {
    "${meters.roundToInt()} м"
  }
}
