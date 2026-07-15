package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

class WatchHistoryUseCase(private val dao: WatchHistoryDao) {
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>> = dao.getRecent(limit)
    suspend fun getByContentId(contentId: String) = dao.getByContentId(contentId)
    suspend fun saveProgress(contentId: String, title: String, poster: String?, positionMs: Long, durationMs: Long) {
        val completed = durationMs > 0 && positionMs.toFloat() / durationMs > 0.95f
        dao.upsert(WatchHistoryEntity(contentId, title, poster, positionMs, durationMs, completed = completed))
    }
    suspend fun delete(contentId: String) = dao.deleteByContentId(contentId)
    suspend fun clearAll() = dao.clearAll()
}
