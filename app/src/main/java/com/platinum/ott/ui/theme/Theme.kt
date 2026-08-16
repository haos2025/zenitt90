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
    surfaceVariant = ZenithSurfaceVariant, onSurfaceVariant = ZenithOnSurfaceVariant,
    background = ZenithBackground,
    error = ZenithError,
)

// РАНЬШЕ ЭТОГО НЕ БЫЛО ВООБЩЕ — существовала только тёмная TV-схема, и
// ZenithTvTheme() не принимала darkTheme параметр в принципе. Хуже того:
// ZenithTvTheme нигде не вызывалась во всём проекте (см. MainActivity.kt)
// — TV-экраны читали androidx.tv.material3.MaterialTheme.colorScheme без
// единого объемлющего TvMaterialTheme{} в дереве композиции, значит
// получали ВСТРОЕННУЮ дефолтную схему библиотеки tv-material3, а не
// ZenithTheme вообще. Именно поэтому переключатель темы "не работал на
// TV" — он в принципе ни на что там не влиял, ни на тёмную, ни на любую
// другую раскраску.
private val TvLightColorScheme = androidx.tv.material3.lightColorScheme(
    primary = ZenithPrimary, onPrimary = Color.White,
    surface = ZenithLightSurface, onSurface = ZenithLightOnSurface,
    surfaceVariant = ZenithLightSurfaceVariant, onSurfaceVariant = ZenithLightOnSurfaceVariant,
    background = ZenithLightBackground,
    error = ZenithError,
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
fun ZenithTvTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    TvMaterialTheme(colorScheme = if (darkTheme) TvColorScheme else TvLightColorScheme, content = content)
}
