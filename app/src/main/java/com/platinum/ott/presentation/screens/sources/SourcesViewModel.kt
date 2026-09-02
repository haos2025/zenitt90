package com.platinum.ott.presentation.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.repository.PlaylistSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Отображаемая модель одного источника — карточка не работает с
// PlaylistSourceEntity напрямую, ей ещё нужно количество контента
// (отдельный подсчёт по playlist_movies) и позиция в списке (для
// вкл/выкл кнопок "приоритет вверх/вниз" — самый первый/последний не
// может двигаться в соответствующую сторону).
data class SourceUiItem(
    val id: String,
    val type: String, // "m3u" | "xtream"
    val label: String,
    val enabled: Boolean,
    val contentCount: Int,
    val lastRefreshedAt: Long?,
    val lastRefreshStatus: String?,
    val isFirst: Boolean,
    val isLast: Boolean
)

sealed interface SourcesUiState {
    data object Loading : SourcesUiState
    data class Success(val sources: List<SourceUiItem>) : SourcesUiState
}

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repository: PlaylistSourceRepository get() = sessionGraph.playlistSourceRepository

    private val _uiState = MutableStateFlow<SourcesUiState>(SourcesUiState.Loading)
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    // Источники, у которых обновление запущено прямо сейчас — отдельно от
    // uiState, чтобы карточка могла показать спиннер на кнопке обновления,
    // не перестраивая (и не мигая) весь список на время одного refresh().
    private val _refreshingIds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingIds: StateFlow<Set<String>> = _refreshingIds.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SourcesUiState.Success(buildItems())
        }
    }

    private suspend fun buildItems(): List<SourceUiItem> {
        val sources = repository.getAll() // уже отсортирован по priority ASC
        return sources.mapIndexed { index, s ->
            SourceUiItem(
                id = s.id,
                type = s.type,
                label = s.label,
                enabled = s.enabled,
                contentCount = repository.getContentCount(s.id),
                lastRefreshedAt = s.lastRefreshedAt,
                lastRefreshStatus = s.lastRefreshStatus,
                isFirst = index == 0,
                isLast = index == sources.lastIndex
            )
        }
    }

    fun refresh(sourceId: String) {
        viewModelScope.launch {
            _refreshingIds.value = _refreshingIds.value + sourceId
            repository.refresh(sourceId, forceRefresh = true)
            _refreshingIds.value = _refreshingIds.value - sourceId
            load()
        }
    }

    fun setEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(sourceId, enabled); load() }
    }

    fun moveUp(sourceId: String) {
        viewModelScope.launch { repository.moveUp(sourceId); load() }
    }

    fun moveDown(sourceId: String) {
        viewModelScope.launch { repository.moveDown(sourceId); load() }
    }

    fun rename(sourceId: String, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { repository.updateLabel(sourceId, label.trim()); load() }
    }

    fun delete(sourceId: String) {
        viewModelScope.launch { repository.delete(sourceId); load() }
    }
}
