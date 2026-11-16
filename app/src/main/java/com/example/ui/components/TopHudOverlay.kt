package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.NavigatorUiState
import com.example.model.SearchSuggestion
import com.example.model.SpeedLimitZone
import com.example.ui.theme.BikeLaneEmerald
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PedestrianBlue
import com.example.ui.theme.ShortcutAmber

@Composable
fun TopHudOverlay(
  uiState: NavigatorUiState,
  onSearchQueryChanged: (String) -> Unit,
  onSearchFocusChanged: (Boolean) -> Unit,
  onSuggestionSelected: (SearchSuggestion) -> Unit,
  onQuickCategorySelected: (String) -> Unit,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 1. Полупрозрачная строка поиска в стиле Яндекс Карт
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
      shadowElevation = 6.dp,
      tonalElevation = 2.dp,
    ) {
      Column {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.ElectricScooter,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp),
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (uiState.searchQuery.isEmpty()) {
              Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = uiState.searchQuery,
              onValueChange = onSearchQueryChanged,
              singleLine = true,
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                  color = MaterialTheme.colorScheme.onSurface,
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions =
                KeyboardActions(
                  onSearch = {
                    focusManager.clearFocus()
                    if (uiState.suggestions.isNotEmpty()) {
                      onSuggestionSelected(uiState.suggestions.first())
                    } else if (uiState.searchQuery.isNotBlank()) {
                      onQuickCategorySelected(uiState.searchQuery)
                    }
                  }
                ),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .testTag("search_input")
                  .onFocusChanged { onSearchFocusChanged(it.isFocused) },
            )
          }

          if (uiState.isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
          }

          if (uiState.searchQuery.isNotEmpty() || uiState.destinationPoint != null) {
            IconButton(
              onClick = {
                focusManager.clearFocus()
                onClearSearch()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.clear_search_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(end = 6.dp),
            )
          }
        }

        // Выпадающий список подсказок SuggestSession
        AnimatedVisibility(
          visible = uiState.suggestions.isNotEmpty() && uiState.isSearchFocused,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            ) {
              items(uiState.suggestions, key = { it.id }) { suggestion ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clickable {
                        focusManager.clearFocus()
                        onSuggestionSelected(suggestion)
                      }
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = suggestion.title,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (suggestion.subtitle.isNotBlank()) {
                      Text(
                        text = suggestion.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  suggestion.distanceText?.let { dist ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = dist,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                  modifier = Modifier.padding(horizontal = 14.dp),
                )
              }
            }
          }
        }
      }
    }

    // 2. Быстрые чипы локаций Алматы (если нет активного маршрута)
    AnimatedVisibility(visible = uiState.destinationPoint == null && !uiState.isSearchFocused) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        QuickPlaceChip(
          icon = Icons.Rounded.Park,
          label = "Парк Первого Президента",
          onClick = { onQuickCategorySelected("Парк Первого Президента Алматы") },
        )
        QuickPlaceChip(
          icon = Icons.AutoMirrored.Rounded.DirectionsBike,
          label = "Арбат / Панфилова",
          onClick = { onQuickCategorySelected("Улица Панфилова Алматы") },
        )
        QuickPlaceChip(
          icon = Icons.Rounded.Subway,
          label = "Метро Абая",
          onClick = { onQuickCategorySelected("Станция метро Абая Алматы") },
        )
        QuickPlaceChip(
          icon = Icons.Rounded.LocationOn,
          label = "Кок-Тобе (нижняя)",
          onClick = { onQuickCategorySelected("Дворец Республики Алматы") },
        )
      }
    }

    // 3. Индикатор скорости ПДД РК (25 / 6 км/ч) и предупреждения на экране
    PddSpeedLimitBanner(uiState = uiState)

    // 4. Предупреждение о настройке API-ключа (если в Secrets оставлен шаблон)
    if (!uiState.isMapKitKeyConfigured) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = ShortcutAmber.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = Color(0xFF16181D),
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Добавьте YANDEX_MAPKIT_API_KEY в панель Secrets для загрузки тайлов и роутинга.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF16181D),
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

@Composable
private fun PddSpeedLimitBanner(uiState: NavigatorUiState) {
  val isPedestrianZone = uiState.activeSpeedZone == SpeedLimitZone.PEDESTRIAN_SIDEWALK
  val isOverLimit = uiState.isNavigating && uiState.currentSpeedKmh > uiState.activeSpeedZone.maxSpeedKmh
  val badgeColor =
    when {
      isOverLimit -> HazardCrimson
      isPedestrianZone -> PedestrianBlue
      else -> BikeLaneEmerald
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Знак ограничения скорости (25 или 6)
      Box(
        modifier =
          Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(2.dp)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "${uiState.activeSpeedZone.maxSpeedKmh}",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text =
              if (isPedestrianZone) {
                stringResource(R.string.speed_limit_pedestrian)
              } else {
                stringResource(R.string.speed_limit_bike_lane)
              },
            style = MaterialTheme.typography.titleSmall,
            color = if (isOverLimit) HazardCrimson else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        val subtitleText =
          uiState.pddAlerts.firstOrNull() ?: stringResource(R.string.pdd_helmet_warning)
        Text(
          text = subtitleText,
          style = MaterialTheme.typography.bodySmall,
          color = if (isOverLimit) HazardCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (uiState.isNavigating || uiState.currentSpeedKmh > 0) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = "${uiState.currentSpeedKmh} км/ч",
            style = MaterialTheme.typography.labelMedium,
            color = if (isOverLimit) HazardCrimson else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun QuickPlaceChip(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.minimumInteractiveComponentSize().clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
    shadowElevation = 3.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}
