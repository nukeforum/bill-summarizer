package com.billsummarizer.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val FallbackDarkScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val FallbackLightScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

private val SolarizedLightScheme =
  lightColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase3,
    primaryContainer = SolarizedBase2,
    onPrimaryContainer = SolarizedBase01,
    secondary = SolarizedCyan,
    onSecondary = SolarizedBase3,
    secondaryContainer = SolarizedBase2,
    onSecondaryContainer = SolarizedBase01,
    tertiary = SolarizedViolet,
    onTertiary = SolarizedBase3,
    tertiaryContainer = SolarizedBase2,
    onTertiaryContainer = SolarizedBase01,
    background = SolarizedBase3,
    onBackground = SolarizedBase00,
    surface = SolarizedBase3,
    onSurface = SolarizedBase00,
    surfaceVariant = SolarizedBase2,
    onSurfaceVariant = SolarizedBase01,
    outline = SolarizedBase1,
    outlineVariant = SolarizedBase2,
    error = SolarizedRed,
    onError = SolarizedBase3,
    errorContainer = SolarizedBase2,
    onErrorContainer = SolarizedRed,
  )

private val SolarizedDarkScheme =
  darkColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase03,
    primaryContainer = SolarizedBase02,
    onPrimaryContainer = SolarizedBase1,
    secondary = SolarizedCyan,
    onSecondary = SolarizedBase03,
    secondaryContainer = SolarizedBase02,
    onSecondaryContainer = SolarizedBase1,
    tertiary = SolarizedViolet,
    onTertiary = SolarizedBase03,
    tertiaryContainer = SolarizedBase02,
    onTertiaryContainer = SolarizedBase1,
    background = SolarizedBase03,
    onBackground = SolarizedBase0,
    surface = SolarizedBase03,
    onSurface = SolarizedBase0,
    surfaceVariant = SolarizedBase02,
    onSurfaceVariant = SolarizedBase1,
    outline = SolarizedBase01,
    outlineVariant = SolarizedBase02,
    error = SolarizedRed,
    onError = SolarizedBase03,
    errorContainer = SolarizedBase02,
    onErrorContainer = SolarizedRed,
  )

@Composable
fun MyApplicationTheme(
  preference: ThemePreference = ThemePreference.DEFAULT,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val (family, isDark) = resolve(preference, systemDark)
  val colorScheme =
    when (family) {
      ThemeFamily.MATERIAL -> materialScheme(LocalContext.current, isDark)
      ThemeFamily.SOLARIZED -> if (isDark) SolarizedDarkScheme else SolarizedLightScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

// Caller in MyApplicationTheme drives this through resolve() which gates the
// Material branch on Build.VERSION_CODES.S. @RequiresApi alone isn't tracked
// through a `when` branch by the Compose lint checker, so we suppress here at
// the API-gated boundary.
@SuppressLint("NewApi")
private fun materialScheme(context: Context, isDark: Boolean): ColorScheme =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    if (isDark) FallbackDarkScheme else FallbackLightScheme
  }
