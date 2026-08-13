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
    fun clearCache() { viewModelScope.launch { sessionGraph.clearCacheUseCase.execute(); sessionGraph.scriptProvider.clearAll() } }
    fun logout() { sessionGraph.logoutUseCase.execute() }

    fun isConnected(): Boolean = sessionGraph.checkAuthUseCase.execute()

    // Раньше здесь был временный двойной вызов ServiceLocator.reinitWithAuth()
    // + sessionGraph.reinitWithAuth() — тот же мост, что и в SetupViewModel.
    // ServiceLocator.kt удалён, оставлен только sessionGraph.reinitWithAuth().
    fun applyNetworkTimeoutChange() { sessionGraph.reinitWithAuth() }

    val darkThemeFlow: StateFlow<Boolean> = themeManager.darkThemeFlow

    fun setDarkTheme(enabled: Boolean) { themeManager.setDarkTheme(enabled) }
}
