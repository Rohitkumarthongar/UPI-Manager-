package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldLight,
  onPrimary = Slate950,
  primaryContainer = EmeraldDark,
  onPrimaryContainer = EmeraldContainer,
  secondary = AccentCyan,
  onSecondary = Slate950,
  secondaryContainer = Slate800,
  onSecondaryContainer = Slate200,
  tertiary = AccentIndigo,
  onTertiary = Color.White,
  background = Slate950,
  onBackground = Slate50,
  surface = Slate900,
  onSurface = Slate50,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate400,
  outline = Slate700,
  error = AccentRose,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldDark,
  onPrimary = Color.White,
  primaryContainer = EmeraldContainer,
  onPrimaryContainer = Slate900,
  secondary = AccentIndigo,
  onSecondary = Color.White,
  secondaryContainer = AccentIndigoContainer,
  onSecondaryContainer = Slate900,
  tertiary = AccentCyan,
  onTertiary = Color.White,
  background = Slate50,
  onBackground = Slate900,
  surface = Color.White,
  onSurface = Slate900,
  surfaceVariant = Slate100,
  onSurfaceVariant = Slate600,
  outline = Slate200,
  error = AccentRose,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Use intentional fintech brand palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
