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

private val DarkColorScheme = darkColorScheme(
    primary = FocusCyanPrimary,
    onPrimary = FocusCyanOnPrimary,
    primaryContainer = FocusCyanContainer,
    onPrimaryContainer = FocusCyanOnContainer,
    secondary = WellnessEmerald,
    onSecondary = Color.White,
    secondaryContainer = WellnessEmeraldContainer,
    onSecondaryContainer = WellnessEmeraldOnContainer,
    tertiary = LockAmber,
    onTertiary = Color.Black,
    tertiaryContainer = LockAmberContainer,
    onTertiaryContainer = LockAmberOnContainer,
    error = RestrictedRed,
    errorContainer = RestrictedRedContainer,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = FocusCyanContainer,
    onPrimary = Color.White,
    primaryContainer = FocusCyanOnContainer,
    onPrimaryContainer = FocusCyanContainer,
    secondary = WellnessEmeraldContainer,
    onSecondary = Color.White,
    secondaryContainer = WellnessEmeraldOnContainer,
    onSecondaryContainer = WellnessEmeraldContainer,
    tertiary = LockAmberContainer,
    onTertiary = Color.White,
    tertiaryContainer = LockAmberOnContainer,
    onTertiaryContainer = LockAmberContainer,
    error = RestrictedRed,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

@Composable
fun FocusGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

// Keep alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    FocusGuardTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
