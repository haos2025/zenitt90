package com.platinum.ott.presentation.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddSourceUiState {
    data object Idle : AddSourceUiState
    data object Loading : AddSourceUiState
    data class Error(val message: String) : AddSourceUiState
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repository get() = sessionGraph.playlistSourceRepository

    private val _uiState = MutableStateFlow<AddSourceUiState>(AddSourceUiState.Idle)
    val uiState: StateFlow<AddSourceUiState> = _uiState.asStateFlow()

    fun addM3uUrl(label: String, url: String, onSuccess: () -> Unit) {
        if (url.isBlank()) { _uiState.value = AddSourceUiState.Error("Введите ссылку"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.validateM3uUrl(url)
                .onSuccess {
                    repository.addM3uUrlSource(label.ifBlank { "Плейлист" }, url)
                    _uiState.value = AddSourceUiState.Idle
                    onSuccess()
                }
                .onFailure { _uiState.value = AddSourceUiState.Error(it.message ?: "Ошибка") }
        }
    }

    // Файл уже прочитан в UI-слое (ACTION_OPEN_DOCUMENT + contentResolver —
    // требует Context, которого у ViewModel по договорённости в проекте нет
    // напрямую, см. остальные *ViewModel-классы). Валидация здесь та же
    // проверка на "#EXTINF", что и для ссылки, просто без сетевого запроса.
    fun addM3uFile(label: String, fileContent: String, onSuccess: () -> Unit) {
        if (!fileContent.contains("#EXTINF")) { _uiState.value = AddSourceUiState.Error("Не M3U-плейлист"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.addM3uFileSource(label.ifBlank { "Локальный файл" }, fileContent)
            _uiState.value = AddSourceUiState.Idle
            onSuccess()
        }
    }

    fun addXtream(label: String, host: String, username: String, password: String, onSuccess: () -> Unit) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = AddSourceUiState.Error("Заполните все поля")
            return
        }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.validateXtream(host, username, password)
                .onSuccess {
                    repository.addXtreamSource(label.ifBlank { "Xtream" }, host, username, password)
                    _uiState.value = AddSourceUiState.Idle
                    onSuccess()
                }
                .onFailure { _uiState.value = AddSourceUiState.Error(it.message ?: "Ошибка") }
        }
    }
}
