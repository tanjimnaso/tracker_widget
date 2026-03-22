package com.example.weighttrackerwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppAccentDark,
    background = AppBackgroundDark,
    surface = AppBackgroundDark,
    onBackground = AppTextPrimaryDark,
    onSurface = AppTextPrimaryDark,
    onSurfaceVariant = AppTextSecondaryDark,
    surfaceVariant = AppCardBackgroundDark
)

private val LightColorScheme = lightColorScheme(
    primary = AppAccentLight,
    background = AppBackgroundLight,
    surface = AppBackgroundLight,
    onBackground = AppTextPrimaryLight,
    onSurface = AppTextPrimaryLight,
    onSurfaceVariant = AppTextSecondaryLight,
    surfaceVariant = AppCardBackgroundLight
)

@Composable
fun WeightTrackerWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to force the custom theme design
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
