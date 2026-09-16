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
            val channels = repository.getAll().sortedWith(
                compareByDescending<ChannelUiItem> { it.isSubscribed }.thenBy { it.canonicalName.lowercase() }
            )
            _uiState.value = ChannelsUiState.Success(channels)
        }
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
