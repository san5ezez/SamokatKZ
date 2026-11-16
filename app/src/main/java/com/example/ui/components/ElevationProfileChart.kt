package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ElevationProfile
import com.example.ui.theme.BikeLaneEmerald
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.ShortcutAmber
import kotlin.math.roundToInt

@Composable
fun ElevationProfileChart(
  profile: ElevationProfile,
  modifier: Modifier = Modifier,
) {
  var selectedIndex by remember(profile) { mutableStateOf<Int?>(null) }
  val samples = profile.samples

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Terrain,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.elevation_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        val inspected = selectedIndex?.let { samples.getOrNull(it) }
        if (inspected != null) {
          Text(
            text = "${inspected.elevationMeters.roundToInt()} м · уклон ${"%.1f".format(inspected.gradePercent)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
        } else {
          Text(
            text = "Макс. уклон ${profile.maxGradePercent}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ElevationMetricChip(
          icon = Icons.AutoMirrored.Rounded.TrendingUp,
          label = "+${profile.totalAscentMeters} м подъём",
          tint = ShortcutAmber,
          modifier = Modifier.weight(1f),
        )
        ElevationMetricChip(
          icon = Icons.AutoMirrored.Rounded.TrendingDown,
          label = "-${profile.totalDescentMeters} м спуск",
          tint = BikeLaneEmerald,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (samples.size >= 2) {
        val lineColor = MaterialTheme.colorScheme.secondary
        val climbColor = ShortcutAmber
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val dotColor = MaterialTheme.colorScheme.primary

        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(110.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
              .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
          Canvas(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(90.dp)
                .pointerInput(samples) {
                  detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    selectedIndex = (ratio * (samples.lastIndex)).roundToInt()
                  }
                }
                .pointerInput(samples) {
                  detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                      val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                      selectedIndex = (ratio * (samples.lastIndex)).roundToInt()
                    },
                  )
                }
          ) {
            val minE = samples.minOf { it.elevationMeters }.toFloat()
            val maxE = samples.maxOf { it.elevationMeters }.toFloat()
            val range = (maxE - minE).coerceAtLeast(8f)

            // Reference horizontal grid lines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (step in 0..2) {
              val y = size.height * (step / 2f)
              drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
              )
            }

            val coords =
              samples.mapIndexed { idx, pt ->
                val x = (idx.toFloat() / samples.lastIndex) * size.width
                val normY = ((pt.elevationMeters.toFloat() - minE) / range).coerceIn(0f, 1f)
                val y = size.height - (normY * (size.height * 0.85f) + size.height * 0.08f)
                Offset(x, y)
              }

            val fillPath =
              Path().apply {
                moveTo(coords.first().x, size.height)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, size.height)
                close()
              }

            drawPath(
              path = fillPath,
              brush =
                Brush.verticalGradient(
                  colors =
                    listOf(
                      lineColor.copy(alpha = 0.35f),
                      lineColor.copy(alpha = 0.02f),
                    )
                ),
            )

            // Draw segments color-coded by ascent vs descent
            for (i in 1 until coords.size) {
              val prev = coords[i - 1]
              val curr = coords[i]
              val grade = samples[i].gradePercent
              val segColor =
                when {
                  grade >= 4.5 -> HazardCrimson
                  grade >= 1.2 -> climbColor
                  else -> lineColor
                }
              drawLine(
                color = segColor,
                start = prev,
                end = curr,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
              )
            }

            val linePath =
              Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                  lineTo(coords[i].x, coords[i].y)
                }
              }
            drawPath(
              path = linePath,
              color = Color.Transparent,
              style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round),
            )

            // Active scrubber marker
            selectedIndex?.let { idx ->
              coords.getOrNull(idx)?.let { pt ->
                drawLine(
                  color = dotColor.copy(alpha = 0.6f),
                  start = Offset(pt.x, 0f),
                  end = Offset(pt.x, size.height),
                  strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(
                  color = Color.White,
                  radius = 6.dp.toPx(),
                  center = pt,
                )
                drawCircle(
                  color = dotColor,
                  radius = 4.dp.toPx(),
                  center = pt,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Старт · ${samples.first().elevationMeters.roundToInt()} м",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${profile.minElevationMeters}–${profile.maxElevationMeters} м",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Финиш · ${samples.last().elevationMeters.roundToInt()} м",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun ElevationMetricChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = tint.copy(alpha = 0.14f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
