package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeralPrimary,
    secondary = TealSecondary,
    tertiary = CoralOrange,
    background = SlateDarkBg,
    surface = SlateCardBg,
    onPrimary = TealSecondary,
    onSecondary = Offwhite,
    onTertiary = Offwhite,
    onBackground = Offwhite,
    onSurface = Offwhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeralPrimary,
    secondary = TealSecondary,
    tertiary = CoralOrange,
    background = Offwhite,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = CharcoalBase,
    onTertiary = CharcoalBase,
    onBackground = SlateDarkBg,
    onSurface = SlateDarkBg
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamicColor by default to enforce our premium customized design palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
