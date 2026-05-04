package com.example.sleepwisepoc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SleepWiseLightColorScheme = lightColorScheme(
    primary              = SleepPrimary,
    onPrimary            = SleepOnPrimary,
    primaryContainer     = SleepPrimaryContainer,
    onPrimaryContainer   = SleepOnPrimaryContainer,
    secondary            = SleepSecondary,
    onSecondary          = SleepOnPrimary,
    secondaryContainer   = SleepSecondaryContainer,
    onSecondaryContainer = SleepOnSecondaryContainer,
    background           = SleepBackground,
    onBackground         = SleepOnBackground,
    surface              = SleepSurface,
    onSurface            = SleepOnBackground,
    surfaceVariant       = SleepSurfaceVariant,
    onSurfaceVariant     = SleepTextSecondary,
    error                = SleepError,
)

private val SleepWiseDarkColorScheme = darkColorScheme(
    primary              = SleepDarkPrimary,
    onPrimary            = SleepDarkOnPrimary,
    primaryContainer     = SleepDarkPrimaryContainer,
    onPrimaryContainer   = SleepDarkPrimary,
    secondary            = SleepSecondary,
    onSecondary          = SleepDarkOnPrimary,
    secondaryContainer   = SleepDarkInsightBg,
    onSecondaryContainer = SleepDarkInsightText,
    background           = SleepDarkBackground,
    onBackground         = SleepDarkOnBackground,
    surface              = SleepDarkSurface,
    onSurface            = SleepDarkOnBackground,
    surfaceVariant       = SleepDarkSurfaceVariant,
    onSurfaceVariant     = SleepDarkTextSecondary,
    error                = SleepError,
)

@Composable
fun SleepWisePOCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — we use our own sleep-friendly palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SleepWiseDarkColorScheme
        else      -> SleepWiseLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
