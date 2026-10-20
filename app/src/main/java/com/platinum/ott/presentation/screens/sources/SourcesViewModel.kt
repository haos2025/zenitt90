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
    val isLast: Boolean,
    // НОВОЕ (подключение reimportFile() к UI): "file://" — локальный
    // снапшот, скопированный один раз при добавлении (см. комментарий у
    // PlaylistSourceEntity.url) — единственный способ обновить такой
    // источник, если исходный файл на диске изменился, это заново выбрать
    // файл и вызвать reimportFile(), обычный "Обновить" перечитает тот же
    // старый снапшот. У источников по ссылке (http/https) этой кнопки нет —
    // refresh() и так перекачивает свежее.
    val isFileSource: Boolean,
    // План 07: сколько потоков-каналов даёт источник (contentCount — записи каталога фильмов/сериалов).
    val channelCount: Int = 0
)

/** Какие источники показывать: все, дающие каналы (вкладка «Источники» в «Каналах») или каталог (Настройки). */
enum class SourceFilter { ALL, CHANNELS, CATALOG }

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

    private var filter = SourceFilter.ALL

    fun setFilter(newFilter: SourceFilter) {
        if (newFilter == filter) return
        filter = newFilter
        load()
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SourcesUiState.Success(buildItems())
        }
    }

    private suspend fun buildItems(): List<SourceUiItem> {
        val sources = repository.getAll() // уже отсортирован по priority ASC
        val all = sources.mapIndexed { index, s ->
            SourceUiItem(
                id = s.id,
                type = s.type,
                label = s.label,
                enabled = s.enabled,
                contentCount = repository.getContentCount(s.id),
                lastRefreshedAt = s.lastRefreshedAt,
                lastRefreshStatus = s.lastRefreshStatus,
                isFirst = index == 0,
                isLast = index == sources.lastIndex,
                isFileSource = s.url?.startsWith("file://") == true,
                channelCount = sessionGraph.channelMatchingRepository.countStreamsBySource(s.id)
            )
        }
        // Пустой (ещё не загруженный или сломанный) источник виден в обоих списках — иначе его нельзя было бы ни обновить, ни удалить.
        return when (filter) {
            SourceFilter.ALL -> all
            SourceFilter.CHANNELS -> all.filter { it.channelCount > 0 || it.contentCount == 0 }
            SourceFilter.CATALOG -> all.filter { it.contentCount > 0 || it.channelCount == 0 }
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

    // НОВОЕ — подключение repository.reimportFile() к UI (см. комментарий
    // у SourceUiItem.isFileSource). Файл уже прочитан в UI-слое, тот же
    // принцип, что и у AddSourceViewModel.addM3uFile().
    fun reimportFile(sourceId: String, fileContent: String) {
        viewModelScope.launch {
            _refreshingIds.value = _refreshingIds.value + sourceId
            repository.reimportFile(sourceId, fileContent)
            _refreshingIds.value = _refreshingIds.value - sourceId
            load()
        }
    }
}
