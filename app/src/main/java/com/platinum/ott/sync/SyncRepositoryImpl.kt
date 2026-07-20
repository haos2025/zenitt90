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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncRepositoryImpl(
    private val api: ZenithApiService,
    private val favDao: FavoritesDao,
    private val histDao: WatchHistoryDao,
    private val prefs: AuthPreferences
) : SyncRepository {
    override suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Раньше запросы вообще не передавали, кто их шлёт — backend не
            // мог бы отличить одно устройство от другого. getOrCreateSyncToken()
            // генерирует и переиспользует стабильный id при первом вызове.
            val deviceId = prefs.getOrCreateSyncToken()
            val since = prefs.lastSyncTimestamp

            // Pull changes from server
            val response = api.getSyncData(deviceId, since)
            response.favorites.forEach { dto ->
                favDao.insertFavorite(FavoriteEntity(contentId = dto.contentId, contentType = dto.contentType, title = dto.title, poster = dto.poster, updatedAt = dto.updatedAt))
            }
            response.watchHistory.forEach { dto ->
                histDao.upsert(WatchHistoryEntity(contentId = dto.contentId, title = dto.title, poster = dto.poster, positionMs = dto.positionMs, durationMs = dto.durationMs, completed = dto.completed))
            }

            // Push local changes — раньше pushData собирался с пустыми
            // favorites/watchHistory: localFavs считывался, но никуда не
            // передавался, поэтому push реально ничего не отправлял.
            val localFavs = favDao.getAllFavorites().first()
            val localHistory = histDao.getSince(since)
            val pushData = SyncPushDto(
                favorites = localFavs.map {
                    FavoriteDto(
                        contentId = it.contentId, contentType = it.contentType,
                        title = it.title, poster = it.poster, updatedAt = it.updatedAt
                    )
                },
                watchHistory = localHistory.map {
                    WatchHistoryDto(
                        contentId = it.contentId, title = it.title, poster = it.poster,
                        positionMs = it.positionMs, durationMs = it.durationMs,
                        completed = it.completed, updatedAt = it.watchedAt
                    )
                },
                clientTimestamp = System.currentTimeMillis()
            )
            api.pushSyncData(deviceId, pushData)

            prefs.lastSyncTimestamp = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createPairingCode(): Result<PairingCode> = withContext(Dispatchers.IO) {
        try {
            val deviceId = prefs.getOrCreateSyncToken()
            val dto = api.createPairingCode(deviceId)
            Result.success(PairingCode(dto.code, dto.expiresInSeconds))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun redeemPairingCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = prefs.getOrCreateSyncToken()
            val response = api.redeemPairingCode(deviceId, com.platinum.ott.data.remote.dto.PairingRedeemDto(code))
            if (response.isSuccessful) {
                // Успешное сопряжение делает старую историю sync неактуальной —
                // после него сервер отдаёт данные ГРУППЫ, а не только этого
                // устройства, поэтому сбрасываем lastSyncTimestamp в 0, чтобы
                // ближайший sync() подтянул вообще всё, а не только то, что
                // "изменилось" относительно старой, чисто персональной точки отсчёта.
                prefs.lastSyncTimestamp = 0
                Result.success(Unit)
            } else {
                Result.failure(Exception("Код истёк или неверен"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
