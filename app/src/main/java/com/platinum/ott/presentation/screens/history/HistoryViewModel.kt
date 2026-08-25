package com.platinum.ott.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    // WatchHistoryUseCase.getRecent() отдаёт плоский список, по одной строке
    // на каждую просмотренную серию — если посмотреть подряд 5 серий одного
    // сериала, в списке было 5 строк. dao.getRecent() уже сортирует по
    // watchedAt DESC, поэтому для схлопывания достаточно взять первую
    // встретившуюся запись на каждый seriesId (она же самая свежая) и
    // пропустить остальные из той же группы; записи без seriesId (обычные
    // фильмы, и старые записи до миграции — там seriesId = null) идут как
    // раньше, по одной. onMovieClick ведёт на contentId именно этой,
    // последней просмотренной серии — DetailViewModel сам сделает редирект
    // на series/{id} через уже существующий механизм (детали см.
    // PROMPT_HISTORY_UPGRADE.md), здесь это не трогаем.
    val history = sessionGraph.watchHistoryUseCase.getRecent().map { entries ->
        val seenSeries = HashSet<String>()
        entries.filter { entry ->
            val seriesId = entry.seriesId
            seriesId == null || seenSeries.add(seriesId)
        }
    }

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
