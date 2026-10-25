package com.nefarioussecurity.sentinel.controller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SentinelColorScheme = darkColorScheme(
    primary = SentinelRed,
    onPrimary = Color.White,
    primaryContainer = SentinelPrimaryContainer,
    onPrimaryContainer = SentinelOnPrimaryContainer,

    secondary = SentinelRedLight,
    onSecondary = Color.White,
    secondaryContainer = SentinelSecondaryContainer,
    onSecondaryContainer = SentinelOnSecondaryContainer,

    tertiary = SentinelRedDark,
    onTertiary = Color.White,

    background = SentinelBlack,
    onBackground = SentinelOnSurface,

    surface = SentinelSurface,
    onSurface = SentinelOnSurface,
    surfaceVariant = SentinelSurfaceVariant,
    onSurfaceVariant = SentinelOnSurfaceVariant,

    error = SentinelRed,
    onError = Color.White,
    errorContainer = SentinelErrorContainer,
    onErrorContainer = SentinelOnErrorContainer,

    outline = SentinelOutline,
    outlineVariant = SentinelOutlineVariant,
)

@Composable
fun SentinelTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = SentinelColorScheme

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            window.statusBarColor = SentinelBlack.toArgb()
            window.navigationBarColor = SentinelBlack.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
