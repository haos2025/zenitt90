package com.platinum.ott.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    // logout()/isConnected() удалены вместе с кнопкой "Сменить аккаунт" и
    // секцией "Аккаунт" (см. SettingsScreen.kt) — задача "Источники"
    // (PROMPT_SOURCES_SCREEN.md) заменила единственный источник на список
    // PlaylistSource, понятия "залогинен/не залогинен" на один аккаунт
    // больше нет. sessionGraph.checkAuthUseCase/loginM3UUseCase/
    // loginXtreamUseCase/logoutUseCase и весь AuthRepository — удалены
    // отдельным аудитом позже (PROMPT_REVISION.md), после подтверждения,
    // что ничего в проекте их больше не вызывает.

    // Раньше здесь был временный двойной вызов ServiceLocator.reinitWithAuth()
    // + sessionGraph.reinitWithAuth() — тот же мост, что и в SetupViewModel.
    // ServiceLocator.kt удалён, оставлен только sessionGraph.reinitWithAuth().
    fun applyNetworkTimeoutChange() { sessionGraph.reinitWithAuth() }

    val darkThemeFlow: StateFlow<Boolean> = themeManager.darkThemeFlow

    fun setDarkTheme(enabled: Boolean) { themeManager.setDarkTheme(enabled) }
}
