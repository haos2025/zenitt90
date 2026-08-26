package com.platinum.ott.presentation.screens.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.domain.model.CacheOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CacheUiState {
    object Loading : CacheUiState
    data class Loaded(val overview: CacheOverview) : CacheUiState
}

/**
 * Общий ViewModel для TV (CacheManagementScreen.kt) и телефона
 * (PhoneCacheManagementScreen.kt) — сам экран отличается только версткой,
 * логика одна. См. CacheManagementUseCase для того, что именно считается
 * каждой категорией.
 */
@HiltViewModel
class CacheManagementViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val _uiState = MutableStateFlow<CacheUiState>(CacheUiState.Loading)
    val uiState: StateFlow<CacheUiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CacheUiState.Loaded(sessionGraph.cacheManagementUseCase.getOverview())
        }
    }

    fun clearCatalog() = clearAndRefresh { sessionGraph.cacheManagementUseCase.clearCatalog() }
    fun clearPlaylist() = clearAndRefresh { sessionGraph.cacheManagementUseCase.clearPlaylist() }
    fun clearMetadata() = clearAndRefresh { sessionGraph.cacheManagementUseCase.clearMetadata() }
    fun clearPosters() = clearAndRefresh { sessionGraph.cacheManagementUseCase.clearPosters() }
    fun clearCrashLogs() = clearAndRefresh { sessionGraph.cacheManagementUseCase.clearCrashLogs() }

    private fun clearAndRefresh(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            _uiState.value = CacheUiState.Loaded(sessionGraph.cacheManagementUseCase.getOverview())
        }
    }
}
