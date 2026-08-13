package com.platinum.ott.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    // Раньше здесь был временный двойной вызов ServiceLocator.reinitWithAuth()
    // + sessionGraph.reinitWithAuth() — мост на время, пока часть экранов
    // (Home, Каталог и т.д.) ещё читала граф через ServiceLocator. Все
    // потребители теперь на SessionGraph, ServiceLocator.kt удалён — мост
    // больше не нужен.
    private val loginM3U = sessionGraph.loginM3UUseCase
    private val loginXtream = sessionGraph.loginXtreamUseCase
    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState

    fun loginWithM3U(url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginM3U.execute(url).onSuccess { sessionGraph.reinitWithAuth(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
    fun loginWithXtream(host: String, user: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginXtream.execute(host, user, pass).onSuccess { sessionGraph.reinitWithAuth(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
}
