package com.platinum.ott.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.domain.usecase.OtaUpdateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SettingsUiState { object Idle : SettingsUiState; object Loading : SettingsUiState; data class Success(val result: OtaUpdateUseCase.OtaResult) : SettingsUiState; data class Error(val message: String) : SettingsUiState }

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState
    fun runOtaUpdate() { viewModelScope.launch { _uiState.value = SettingsUiState.Loading; ServiceLocator.otaUpdateUseCase.execute().onSuccess { _uiState.value = SettingsUiState.Success(it) }.onFailure { _uiState.value = SettingsUiState.Error(it.message ?: "Ошибка") } } }
    fun clearCache() { viewModelScope.launch { ServiceLocator.clearCacheUseCase.execute(); ServiceLocator.scriptProvider.clearAll() } }
    fun logout() { ServiceLocator.logoutUseCase.execute() }
}
