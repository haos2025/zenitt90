package com.platinum.ott.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {
    private val loginM3U = ServiceLocator.loginM3UUseCase
    private val loginXtream = ServiceLocator.loginXtreamUseCase
    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState

    fun loginWithM3U(url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginM3U.execute(url).onSuccess { ServiceLocator.reinitWithAuth(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
    fun loginWithXtream(host: String, user: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginXtream.execute(host, user, pass).onSuccess { ServiceLocator.reinitWithAuth(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
}
