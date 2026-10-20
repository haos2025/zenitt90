package com.platinum.ott.presentation.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.playlist.EntryClassifier
import com.platinum.ott.data.repository.GroupStat
import com.platinum.ott.data.repository.PlaylistSourceRepository
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

    /** Источник добавлен и загружен: итог разведения (план 07, раздел 6). [error] — источник добавлен, но загрузка не удалась. */
    data class Result(
        val sourceId: String,
        val label: String,
        val isM3u: Boolean,
        val channels: Int,
        val vod: Int,
        val error: String? = null
    ) : AddSourceUiState
}

/** Состояние экрана «Разделено неверно?»: список групп и выбранные (ещё не применённые) переопределения. */
data class GroupsUiState(
    val loading: Boolean = true,
    val groups: List<GroupStat> = emptyList(),
    val pending: Map<String, String> = emptyMap(),
    val saving: Boolean = false
)

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repository get() = sessionGraph.playlistSourceRepository

    private val _uiState = MutableStateFlow<AddSourceUiState>(AddSourceUiState.Idle)
    val uiState: StateFlow<AddSourceUiState> = _uiState.asStateFlow()

    fun addM3uUrl(label: String, url: String, isLiveChannels: Boolean, onSuccess: () -> Unit) {
        if (url.isBlank()) { _uiState.value = AddSourceUiState.Error("Введите ссылку"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.validateM3uUrl(url)
                .onSuccess {
                    repository.addM3uUrlSource(label.ifBlank { "Плейлист" }, url, isLiveChannels)
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
    fun addM3uFile(label: String, fileContent: String, isLiveChannels: Boolean, onSuccess: () -> Unit) {
        if (!fileContent.contains("#EXTINF")) { _uiState.value = AddSourceUiState.Error("Не M3U-плейлист"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.addM3uFileSource(label.ifBlank { "Локальный файл" }, fileContent, isLiveChannels)
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

    // ---------- План 07, этап 3: добавление с итогом разведения ----------

    /** Режим разведения M3U: "auto" | "live" | "vod" (EntryClassifier.MODE_*). */
    fun addM3uUrlWithResult(label: String, url: String, mode: String) {
        if (url.isBlank()) { _uiState.value = AddSourceUiState.Error("Введите ссылку"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.validateM3uUrl(url)
                .onSuccess {
                    val source = repository.addM3uUrlSource(label.ifBlank { "Плейлист" }, url, mode = mode)
                    finishAdd(source.id, source.label, isM3u = true)
                }
                .onFailure { _uiState.value = AddSourceUiState.Error(it.message ?: "Ошибка") }
        }
    }

    fun addM3uFileWithResult(label: String, fileContent: String, mode: String) {
        if (!fileContent.contains("#EXTINF")) { _uiState.value = AddSourceUiState.Error("Не M3U-плейлист"); return }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            val source = repository.addM3uFileSource(label.ifBlank { "Локальный файл" }, fileContent, mode = mode)
            finishAdd(source.id, source.label, isM3u = true)
        }
    }

    fun addXtreamWithResult(label: String, host: String, username: String, password: String) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = AddSourceUiState.Error("Заполните все поля")
            return
        }
        viewModelScope.launch {
            _uiState.value = AddSourceUiState.Loading
            repository.validateXtream(host, username, password)
                .onSuccess {
                    val source = repository.addXtreamSource(label.ifBlank { "Xtream" }, host, username, password)
                    finishAdd(source.id, source.label, isM3u = false)
                }
                .onFailure { _uiState.value = AddSourceUiState.Error(it.message ?: "Ошибка") }
        }
    }

    /** Первая загрузка источника (сразу, а не «когда-нибудь потом») — чтобы показать итог. */
    private suspend fun finishAdd(sourceId: String, label: String, isM3u: Boolean) {
        repository.refresh(sourceId, forceRefresh = true)
        val status = repository.getAll().firstOrNull { it.id == sourceId }?.lastRefreshStatus
        val split = repository.lastSplit(sourceId)
        _uiState.value = AddSourceUiState.Result(
            sourceId = sourceId,
            label = label,
            isM3u = isM3u,
            channels = split?.channels ?: 0,
            vod = split?.vod ?: 0,
            error = if (status != null && status != "ok") status else null
        )
    }

    /** «Готово» на экране результата: сбросить состояние (экран закроется сам). */
    fun resetState() { _uiState.value = AddSourceUiState.Idle }

    // ---------- «Разделено неверно?»: группы источника ----------

    private val _groups = MutableStateFlow(GroupsUiState())
    val groups: StateFlow<GroupsUiState> = _groups.asStateFlow()

    fun loadGroups(sourceId: String) {
        viewModelScope.launch {
            _groups.value = GroupsUiState(loading = true)
            val list = repository.getGroupStats(sourceId)
            _groups.value = GroupsUiState(
                loading = false,
                groups = list,
                pending = list.mapNotNull { g -> g.override?.let { g.name to it } }.toMap()
            )
        }
    }

    /** value: "live" | "vod" | null (сброс). Применяется кнопкой «Применить» — не на каждое нажатие. */
    fun setGroupChoice(group: String, value: String?) {
        val cur = _groups.value
        _groups.value = cur.copy(pending = if (value == null) cur.pending - group else cur.pending + (group to value))
    }

    /** Сохранить выбор и пересчитать разведение; затем обновить итог на экране результата. */
    fun applyGroups(sourceId: String, onApplied: () -> Unit) {
        val cur = _groups.value
        if (cur.saving) return
        _groups.value = cur.copy(saving = true)
        viewModelScope.launch {
            repository.setGroupOverrides(sourceId, cur.pending)
            val prev = _uiState.value
            val split = repository.lastSplit(sourceId)
            if (prev is AddSourceUiState.Result && split != null) {
                _uiState.value = prev.copy(channels = split.channels, vod = split.vod)
            }
            _groups.value = _groups.value.copy(saving = false)
            onApplied()
        }
    }
}
