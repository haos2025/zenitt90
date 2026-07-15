package com.platinum.ott.sync

import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.FavoriteDto
import com.platinum.ott.data.remote.dto.WatchHistoryDto
import com.platinum.ott.data.remote.dto.SyncPushDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepositoryImpl(
    private val api: ZenithApiService,
    private val favDao: FavoritesDao,
    private val histDao: WatchHistoryDao,
    private val prefs: AuthPreferences
) : SyncRepository {
    override suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val since = prefs.lastSyncTimestamp
            // Pull changes from server
            val response = api.getSyncData(since)
            response.favorites.forEach { dto ->
                favDao.insertFavorite(FavoriteEntity(contentId = dto.contentId, contentType = dto.contentType, title = dto.title, poster = dto.poster, updatedAt = dto.updatedAt))
            }
            response.watchHistory.forEach { dto ->
                histDao.upsert(WatchHistoryEntity(contentId = dto.contentId, title = dto.title, poster = dto.poster, positionMs = dto.positionMs, durationMs = dto.durationMs, completed = dto.completed))
            }
            // Push local changes
            val localFavs = favDao.getAllFavorites() // Flow, need to handle differently in production
            val pushData = SyncPushDto(clientTimestamp = System.currentTimeMillis())
            api.pushSyncData(pushData)
            prefs.lastSyncTimestamp = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
