package com.platinum.ott.core.di

import com.platinum.ott.core.InterfacePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Раньше darkThemeFlow/setDarkTheme жили прямо в ServiceLocator как
 * "Interface (тема) — независимо от авторизации" (их же комментарий).
 * Здесь — тот же код, тот же дефолт (значение из текущих Preferences при
 * создании), та же семантика: меняется только через setDarkTheme().
 */
@Singleton
class ThemeManager @Inject constructor(
    private val interfacePreferences: InterfacePreferences
) {
    val darkThemeFlow = MutableStateFlow(interfacePreferences.isDarkTheme)

    fun setDarkTheme(enabled: Boolean) {
        interfacePreferences.isDarkTheme = enabled
        darkThemeFlow.value = enabled
    }
}
