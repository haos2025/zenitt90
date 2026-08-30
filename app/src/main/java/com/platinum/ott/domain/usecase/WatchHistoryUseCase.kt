package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchHistoryUseCase(private val dao: WatchHistoryDao) {
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>> = dao.getRecent(limit)

    // WatchHistoryUseCase.getRecent() отдаёт плоский список, по одной строке
    // на каждую просмотренную серию — если посмотреть подряд 5 серий одного
    // сериала, в списке было 5 строк. dao.getRecent() уже сортирует по
    // watchedAt DESC, поэтому для схлопывания достаточно взять первую
    // встретившуюся запись на каждый seriesId (она же самая свежая) и
    // пропустить остальные из той же группы; записи без seriesId (обычные
    // фильмы, и старые записи до миграции — там seriesId = null) идут как
    // раньше, по одной.
    //
    // Раньше это жило только внутри HistoryViewModel — вынесено сюда, чтобы
    // HomeViewModel (ряд "Продолжить просмотр", PROMPT_HOME_FEED_REDESIGN.md)
    // переиспользовал готовый результат напрямую, не изобретая вторую копию
    // того же алгоритма (см. HistoryViewModel.kt — теперь тоже вызывает это).
    fun getRecentDeduped(limit: Int = 50): Flow<List<WatchHistoryEntity>> = getRecent(limit).map { entries ->
        val seenSeries = HashSet<String>()
        entries.filter { entry ->
            val seriesId = entry.seriesId
            seriesId == null || seenSeries.add(seriesId)
        }
    }

    suspend fun getByContentId(contentId: String) = dao.getByContentId(contentId)
    suspend fun getByContentIds(contentIds: List<String>) = dao.getByContentIds(contentIds)
    suspend fun saveProgress(contentId: String, title: String, poster: String?, positionMs: Long, durationMs: Long, seriesId: String? = null) {
        val completed = durationMs > 0 && positionMs.toFloat() / durationMs > 0.95f
        dao.upsert(WatchHistoryEntity(contentId, title, poster, positionMs, durationMs, completed = completed, seriesId = seriesId))
    }
    suspend fun delete(contentId: String) = dao.deleteByContentId(contentId)
    suspend fun clearAll() = dao.clearAll()
}
