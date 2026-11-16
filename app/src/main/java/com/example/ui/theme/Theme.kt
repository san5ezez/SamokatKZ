package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = SignalYellow,
    onPrimary = DeepObsidian,
    primaryContainer = Color(0xFF3A310E),
    onPrimaryContainer = SignalYellow,
    secondary = BikeLaneEmeraldLight,
    onSecondary = Color(0xFF062617),
    secondaryContainer = Color(0xFF0C3824),
    onSecondaryContainer = BikeLaneEmeraldLight,
    tertiary = PedestrianBlueLight,
    onTertiary = Color(0xFF061A3D),
    tertiaryContainer = Color(0xFF102A54),
    onTertiaryContainer = PedestrianBlueLight,
    error = HazardCrimsonLight,
    onError = Color(0xFF3D0604),
    errorContainer = Color(0xFF471210),
    onErrorContainer = Color(0xFFFFB4AB),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SignalYellowDark,
    onPrimary = DeepObsidian,
    primaryContainer = Color(0xFFFFF3C4),
    onPrimaryContainer = Color(0xFF332600),
    secondary = BikeLaneEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF7EB),
    onSecondaryContainer = Color(0xFF003D22),
    tertiary = PedestrianBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1ECFF),
    onTertiaryContainer = Color(0xFF002466),
    error = HazardCrimson,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF5C0A05),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
  )

@Composable
fun SamokatTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
