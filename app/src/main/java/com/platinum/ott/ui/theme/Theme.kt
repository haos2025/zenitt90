package com.platinum.ott.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

// Приложение только тёмное (светлую тему убрали — см. Color.kt).

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent, onPrimary = ZenithOnPrimary,
    secondary = ZenithSecondary, error = ZenithError,
    background = ZenithBackground, surface = ZenithSurface,
    surfaceVariant = ZenithSurfaceVariant,
    onBackground = ZenithOnSurface, onSurface = ZenithOnSurface,
    onSurfaceVariant = ZenithOnSurfaceVariant,
)

@OptIn(ExperimentalTvMaterial3Api::class)
private fun tvScheme(accent: Color) = tvDarkColorScheme(
    primary = accent, onPrimary = ZenithOnPrimary,
    surface = ZenithSurface, onSurface = ZenithOnSurface,
    surfaceVariant = ZenithSurfaceVariant, onSurfaceVariant = ZenithOnSurfaceVariant,
    background = ZenithBackground,
    error = ZenithError,
)

/** isTv — крупнее текст для material3-виджетов внутри TV-экранов (диалоги и т.п.). */
@Composable
fun ZenithTheme(isTv: Boolean = false, accent: AccentColor = AccentColor.CYAN, content: @Composable () -> Unit) {
    val scheme = remember(accent) { darkScheme(accent.color) }
    MaterialTheme(
        colorScheme = scheme,
        typography = zenithTypography(isTv),
        shapes = ZenithShapes,
        content = content,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZenithTvTheme(accent: AccentColor = AccentColor.CYAN, content: @Composable () -> Unit) {
    val scheme = remember(accent) { tvScheme(accent.color) }
    TvMaterialTheme(
        colorScheme = scheme,
        typography = ZenithTvTypography,
        shapes = ZenithTvShapes,
        content = content,
    )
}
