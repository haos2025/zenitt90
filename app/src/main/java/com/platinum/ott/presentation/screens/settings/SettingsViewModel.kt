package com.platinum.ott.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.core.di.ThemeManager
import com.platinum.ott.domain.usecase.OtaUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState { object Idle : SettingsUiState; object Loading : SettingsUiState; data class Success(val result: OtaUpdateUseCase.OtaResult) : SettingsUiState; data class Error(val message: String) : SettingsUiState }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionGraph: SessionGraph,
    private val themeManager: ThemeManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun runOtaUpdate() { viewModelScope.launch { _uiState.value = SettingsUiState.Loading; sessionGraph.otaUpdateUseCase.execute().onSuccess { _uiState.value = SettingsUiState.Success(it) }.onFailure { _uiState.value = SettingsUiState.Error(it.message ?: "Ошибка") } } }
    fun clearCache() { viewModelScope.launch { sessionGraph.clearCacheUseCase.execute(); sessionGraph.scriptProvider.clearAll() } }
    fun logout() { sessionGraph.logoutUseCase.execute() }

    // Раньше SettingsScreen.kt/PhoneSettingsScreen.kt дергали
    // ServiceLocator.checkAuthUseCase.execute() прямо из композабла.
    // Чтение не меняет граф — не важно, через ServiceLocator или через
    // sessionGraph.checkAuthUseCase.execute() это читать: оба смотрят на
    // один и тот же файл AuthPreferences на диске.
    fun isConnected(): Boolean = sessionGraph.checkAuthUseCase.execute()

    // ВРЕМЕННЫЙ МОСТ переходного периода (тот же принцип, что и в
    // SetupViewModel.reinitBothGraphs()): смена таймаута сети должна
    // применяться и для экранов на ServiceLocator (большинство
    // приложения), и для уже мигрированного SessionGraph. Убрать вызов
    // ServiceLocator.reinitWithAuth() здесь, когда ServiceLocator.kt будет
    // удалён целиком.
    fun applyNetworkTimeoutChange() {
        ServiceLocator.reinitWithAuth()
        sessionGraph.reinitWithAuth()
    }

    // Тема: MainActivity.kt пока продолжает читать ServiceLocator.darkThemeFlow
    // напрямую (не мигрирован в этом заходе) — он остаётся источником
    // истины для реального рендера темы. themeManager.setDarkTheme()
    // вызывается ДОПОЛНИТЕЛЬНО, чтобы уже готовый Hilt-синглтон
    // (SessionGraph di/ThemeManager) не разъезжался с реальным состоянием,
    // когда до него дойдёт очередь. darkThemeFlow здесь — тот же поток,
    // что видит MainActivity, экран настроек им не подменяет, только
    // ретранслирует наружу, чтобы композаблы не трогали ServiceLocator
    // напрямую.
    val darkThemeFlow: StateFlow<Boolean> = ServiceLocator.darkThemeFlow

    fun setDarkTheme(enabled: Boolean) {
        ServiceLocator.setDarkTheme(enabled)
        themeManager.setDarkTheme(enabled)
    }
}
