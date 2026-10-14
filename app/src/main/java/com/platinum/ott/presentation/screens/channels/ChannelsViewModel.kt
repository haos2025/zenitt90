package com.platinum.ott.presentation.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.repository.ChannelHealthChecker
import com.platinum.ott.data.repository.ChannelRepository
import com.platinum.ott.data.repository.ChannelUiItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data class Success(val channels: List<ChannelUiItem>) : ChannelsUiState
}

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repository: ChannelRepository get() = sessionGraph.channelRepository
    private val healthChecker: ChannelHealthChecker get() = sessionGraph.channelHealthChecker

    private val _uiState = MutableStateFlow<ChannelsUiState>(ChannelsUiState.Loading)
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    // Отдельно от uiState — тот же приём, что и refreshingIds в
    // SourcesViewModel.kt: чтобы карточка/кнопка "Проверить" могла
    // показать спиннер, не перестраивая весь список на время проверки.
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Подписанные — сначала, внутри группы — по алфавиту; так
            // пользователь сразу видит свой рабочий список каналов, а
            // разбор кандидатов на подписку/слияние — ниже него.
            // Ошибка чтения БД не должна ронять приложение — показываем пустой список.
            val channels = try {
                repository.getAll().sortedWith(
                    compareByDescending<ChannelUiItem> { it.isSubscribed }.thenBy { it.canonicalName.lowercase() }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ChannelsViewModel", "load failed", e)
                emptyList()
            }
            _uiState.value = ChannelsUiState.Success(channels)
        }
    }

    /** «Сейчас в эфире» для строки списка (телефон): название передачи и доля прошедшего (0..1). */
    suspend fun nowPlaying(channelId: String): Pair<String, Float>? = try {
        val now = System.currentTimeMillis()
        sessionGraph.epgProgramRepository.getCurrentForChannel(channelId, now)?.let { p ->
            val total = (p.endTimeMillis - p.startTimeMillis).coerceAtLeast(1L)
            p.title to ((now - p.startTimeMillis).toFloat() / total).coerceIn(0f, 1f)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Сейчас/далее для панели выбранного канала (ТВ). */
    data class NowNext(
        val title: String, val progress: Float, val startMs: Long, val endMs: Long,
        val description: String?, val nextTitle: String?
    )

    suspend fun nowNext(channelId: String): NowNext? = try {
        val now = System.currentTimeMillis()
        val repo = sessionGraph.epgProgramRepository
        repo.getCurrentForChannel(channelId, now)?.let { p ->
            val total = (p.endTimeMillis - p.startTimeMillis).coerceAtLeast(1L)
            val next = repo.getForChannelInRange(channelId, p.endTimeMillis, p.endTimeMillis + 6 * 3_600_000L)
                .filter { it.startTimeMillis >= p.endTimeMillis }.minByOrNull { it.startTimeMillis }
            NowNext(p.title, ((now - p.startTimeMillis).toFloat() / total).coerceIn(0f, 1f), p.startTimeMillis, p.endTimeMillis, p.description, next?.title)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun setSubscribed(channelId: String, isSubscribed: Boolean) {
        viewModelScope.launch { repository.setSubscribed(channelId, isSubscribed); load() }
    }

    fun rename(channelId: String, name: String, regionHint: String?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(channelId, name.trim(), regionHint?.trim()?.ifBlank { null }); load() }
    }

    fun delete(channelId: String) {
        viewModelScope.launch { repository.delete(channelId); load() }
    }

    fun merge(sourceChannelId: String, targetChannelId: String) {
        viewModelScope.launch { repository.merge(sourceChannelId, targetChannelId); load() }
    }

    /**
     * "Проверить сейчас" на весь список — уважает бэкофф ChannelHealthChecker
     * (isDue()), то есть заведомо не перепроверит стрим, который недавно
     * уже проверялся и был жив — это НЕ принудительная проверка каждого
     * стрима без исключения, только сброс ожидания "по расписанию раз в
     * 30 минут" (ChannelHealthCheckWorker) на "прямо сейчас, раз попросили".
     */
    fun checkAll() {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            try {
                healthChecker.checkSubscribedChannels()
                load()
            } finally {
                _isChecking.value = false
            }
        }
    }

    /** Точечная проверка одного канала — из его строки/меню в списке. */
    fun checkOne(channelId: String) {
        if (_isChecking.value) return
        viewModelScope.launch {
            _isChecking.value = true
            try {
                healthChecker.checkChannel(channelId)
                load()
            } finally {
                _isChecking.value = false
            }
        }
    }
}
