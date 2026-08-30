package com.platinum.ott.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    // Группировка серий сериала в одну запись ("Продолжить [Название]")
    // теперь живёт в WatchHistoryUseCase.getRecentDeduped() — вынесена туда,
    // чтобы HomeViewModel (ряд "Продолжить просмотр",
    // PROMPT_HOME_FEED_REDESIGN.md) переиспользовал тот же алгоритм, а не
    // держал вторую копию. onMovieClick по-прежнему ведёт на contentId
    // именно последней просмотренной серии — DetailViewModel сам сделает
    // редирект на series/{id} через уже существующий механизм.
    val history = sessionGraph.watchHistoryUseCase.getRecentDeduped()

    // WatchHistoryUseCase.delete()/clearAll() существовали с самого начала,
    // но ни один экран их не вызывал (см. PROMPT_HISTORY_UPGRADE.md, п.1) —
    // просто прокидываем их сюда для HistoryScreen/PhoneHistoryScreen.
    fun delete(entry: WatchHistoryEntity) {
        viewModelScope.launch { sessionGraph.watchHistoryUseCase.delete(entry.contentId) }
    }

    fun clearAll() {
        viewModelScope.launch { sessionGraph.watchHistoryUseCase.clearAll() }
    }
}
