package com.example.sleepwisepoc.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Nightfold tokens for composables: `val c = LocalSleepColors.current`
val LocalSleepColors = staticCompositionLocalOf { nightAppColors }

private val NightfoldLight = lightColorScheme(
    primary = NfLightAccent, onPrimary = NfLightOnAccent,
    secondary = NfLightStageRem, onSecondary = NfLightOnAccent,
    background = NfLightBg, onBackground = NfLightText,
    surface = NfLightSurface, onSurface = NfLightText,
    surfaceVariant = NfLightSurface2, onSurfaceVariant = NfLightDim,
    outline = NfLightLineStrong, error = SleepError,
)

private val NightfoldDark = darkColorScheme(
    primary = NfDarkAccent, onPrimary = NfDarkOnAccent,
    secondary = NfDarkStageRem, onSecondary = NfDarkOnAccent,
    background = NfDarkBg, onBackground = NfDarkText,
    surface = NfDarkSurface, onSurface = NfDarkText,
    surfaceVariant = NfDarkSurface2, onSurfaceVariant = NfDarkDim,
    outline = NfDarkLineStrong, error = SleepError,
)

@Composable
fun SleepWisePOCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // Nightfold uses its own palette; dynamic color off
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NightfoldDark else NightfoldLight
    val appColors = if (darkTheme) nightAppColors else dayAppColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = appColors.bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSleepColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
