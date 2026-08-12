package com.platinum.ott.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
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
    // Сам логин-юзкейс пока намеренно остаётся на ServiceLocator, а не на
    // sessionGraph.loginM3UUseCase/loginXtreamUseCase: большая часть
    // приложения (Home, Каталог, Плеер, Sync, Избранное) ещё читает именно
    // ServiceLocator-граф, не SessionGraph. Логин — точка входа, от
    // которой зависит весь граф целиком, поэтому исполнение через
    // ServiceLocator остаётся источником истины до тех пор, пока эти
    // экраны не мигрируют.
    private val loginM3U = ServiceLocator.loginM3UUseCase
    private val loginXtream = ServiceLocator.loginXtreamUseCase
    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState

    // ВРЕМЕННЫЙ МОСТ переходного периода: reinitWithAuth() дергается на
    // ОБОИХ графах, потому что уже есть мигрированный на SessionGraph
    // экран (SettingsViewModel), которому тоже нужны свежие креды после
    // логина, а не только ServiceLocator-потребителям. Убрать эту функцию
    // и вызывать только sessionGraph.reinitWithAuth() в тот момент, когда
    // ServiceLocator.kt будет удалён целиком (см. REFACTOR_PROMPT.md).
    private fun reinitBothGraphs() {
        ServiceLocator.reinitWithAuth()
        sessionGraph.reinitWithAuth()
    }

    fun loginWithM3U(url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginM3U.execute(url).onSuccess { reinitBothGraphs(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
    fun loginWithXtream(host: String, user: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            loginXtream.execute(host, user, pass).onSuccess { reinitBothGraphs(); onSuccess() }
                .onFailure { _uiState.value = SetupUiState.Error(it.message ?: "Ошибка") }
        }
    }
}
