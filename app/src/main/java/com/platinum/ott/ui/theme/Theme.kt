package com.platinum.ott.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

// Приложение только тёмное (светлую тему убрали — см. Color.kt).

private val DarkColorScheme = darkColorScheme(
    primary = ZenithPrimary, onPrimary = ZenithOnPrimary,
    secondary = ZenithSecondary, error = ZenithError,
    background = ZenithBackground, surface = ZenithSurface,
    surfaceVariant = ZenithSurfaceVariant,
    onBackground = ZenithOnSurface, onSurface = ZenithOnSurface,
    onSurfaceVariant = ZenithOnSurfaceVariant,
)

private val TvColorScheme = tvDarkColorScheme(
    primary = ZenithPrimary, onPrimary = ZenithOnPrimary,
    surface = ZenithSurface, onSurface = ZenithOnSurface,
    surfaceVariant = ZenithSurfaceVariant, onSurfaceVariant = ZenithOnSurfaceVariant,
    background = ZenithBackground,
    error = ZenithError,
)

/** isTv — крупнее текст для material3-виджетов внутри TV-экранов (диалоги и т.п.). */
@Composable
fun ZenithTheme(isTv: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = zenithTypography(isTv),
        shapes = ZenithShapes,
        content = content,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZenithTvTheme(content: @Composable () -> Unit) {
    TvMaterialTheme(
        colorScheme = TvColorScheme,
        typography = ZenithTvTypography,
        shapes = ZenithTvShapes,
        content = content,
    )
}
