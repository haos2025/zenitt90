package com.platinum.ott.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = ZenithPrimary, onPrimary = ZenithOnPrimary,
    secondary = ZenithSecondary, error = ZenithError,
    background = ZenithBackground, surface = ZenithSurface,
    surfaceVariant = ZenithSurfaceVariant,
    onBackground = ZenithOnSurface, onSurface = ZenithOnSurface,
    onSurfaceVariant = ZenithOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = ZenithPrimary, onPrimary = Color.White,
    secondary = ZenithSecondary, error = ZenithError,
    background = ZenithLightBackground, surface = ZenithLightSurface,
    surfaceVariant = ZenithLightSurfaceVariant,
    onBackground = ZenithLightOnSurface, onSurface = ZenithLightOnSurface,
    onSurfaceVariant = ZenithLightOnSurfaceVariant,
)

private val TvColorScheme = tvDarkColorScheme(
    primary = ZenithPrimary, onPrimary = ZenithOnPrimary,
    surface = ZenithSurface, onSurface = ZenithOnSurface,
    background = ZenithBackground,
)

@Composable
fun ZenithTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ZenithTypography,
        content = content,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZenithTvTheme(content: @Composable () -> Unit) {
    TvMaterialTheme(colorScheme = TvColorScheme, content = content)
}
