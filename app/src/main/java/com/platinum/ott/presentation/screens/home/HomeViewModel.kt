package com.platinum.ott.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val getCatalog = ServiceLocator.getCatalogUseCase
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init { loadCatalog() }

    fun loadCatalog(page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            getCatalog.execute(page).onSuccess { _uiState.value = HomeUiState.Success(it.movies, it.currentPage, it.totalPages) }
                .onFailure { _uiState.value = HomeUiState.Error(it.message ?: "Ошибка загрузки") }
        }
    }
}
