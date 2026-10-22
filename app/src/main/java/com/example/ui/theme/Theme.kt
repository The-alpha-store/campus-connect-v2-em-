package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryDark,
  onPrimary = Color(0xFF001E6C),
  primaryContainer = DeepBlueDark,
  onPrimaryContainer = Color(0xFFDAE2FF),
  secondary = WarmAmber,
  onSecondary = Color(0xFF452B00),
  secondaryContainer = Color(0xFF633F00),
  onSecondaryContainer = WarmAmberLight,
  tertiary = SuccessGreen,
  background = BackgroundDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = TextSecondaryDark,
  outline = OutlineDark,
  error = DangerRed
)

private val LightColorScheme = lightColorScheme(
  primary = DeepBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDDE3FF),
  onPrimaryContainer = Color(0xFF001453),
  secondary = WarmAmber,
  onSecondary = Color.White,
  secondaryContainer = WarmAmberLight,
  onSecondaryContainer = WarmAmberDark,
  tertiary = SuccessGreen,
  background = BackgroundLight,
  onBackground = TextPrimaryLight,
  surface = SurfaceLight,
  onSurface = TextPrimaryLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = TextSecondaryLight,
  outline = OutlineLight,
  error = DangerRed
)

@Composable
fun CampusConnectTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
