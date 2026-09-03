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
            val deviceId = prefs.getOrCreateSyncToken()
            val since = prefs.lastSyncTimestamp

            // Pull changes from server
            val response = api.getSyncData(deviceId, since)
            response.favorites.forEach { dto ->
                // Диагностика "источники/избранное синхронизируются плохо":
                // FavoriteEntity.id — autoGenerate PK, НЕ contentId. insertFavorite()
                // с OnConflictStrategy.REPLACE конфликтует только по PK (id
                // всегда 0 у новой записи → Room всегда создаёт новую строку,
                // никогда не считает это конфликтом). Раньше это означало, что
                // КАЖДЫЙ sync ДУБЛИРОВАЛ все избранное, пришедшее с другого
                // устройства, вместо обновления существующей записи по
                // contentId — список избранного пух с каждым нажатием
                // "Синхронизировать". Убираем старую запись по contentId перед
                // вставкой, чтобы insert реально вёл себя как upsert.
                favDao.deleteByContentId(dto.contentId)
                favDao.insertFavorite(
                    FavoriteEntity(
                        contentId = dto.contentId, contentType = dto.contentType,
                        title = dto.title, poster = dto.poster,
                        // Раньше addedAt пулленной записи всегда становился
                        // "сейчас" (значение по умолчанию в конструкторе,
                        // вычисляется на момент вызова) — избранное с другого
                        // устройства всегда прыгало в начало списка ("недавно
                        // добавленное" сверху, сортировка по addedAt DESC),
                        // независимо от реальной даты добавления, и порядок
                        // списка визуально ломался при каждой синхронизации.
                        addedAt = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis(),
                        updatedAt = dto.updatedAt
                    )
                )
            }
            response.watchHistory.forEach { dto ->
                histDao.upsert(
                    WatchHistoryEntity(
                        contentId = dto.contentId, title = dto.title, poster = dto.poster,
                        positionMs = dto.positionMs, durationMs = dto.durationMs,
                        // Та же ошибка, что и у избранного: watchedAt пулленной
                        // записи тоже всегда становился "сейчас". Это ломало
                        // и порядок "Продолжить просмотр" (сортировка по
                        // watchedAt DESC — только что просмотренное на ДРУГОМ
                        // устройстве несколько дней назад выглядело так, будто
                        // его посмотрели только что), И следующий push этого
                        // устройства: только что подтянутая запись сразу
                        // попадала под getSince(since) как будто это свежее
                        // ЛОКАЛЬНОЕ изменение и уходила обратно на сервер тем
                        // же циклом синхронизации — бесполезный трафик, а на
                        // практике часть записей могла вообще не доходить,
                        // если бэкенд отбрасывает/схлопывает такие "эхо"-записи
                        // по contentId без более свежего реального изменения.
                        watchedAt = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis(),
                        completed = dto.completed
                        // seriesId сюда сознательно не попадает — WatchHistoryDto
                        // (SyncDtos.kt) вообще не содержит этого поля, бэкенд
                        // про него не знает. Это отдельная задача, требующая
                        // изменения и на бэкенде (недоступен для правки
                        // отсюда — отдельный репозиторий zenith-backend), и
                        // здесь. Практическое следствие прямо сейчас: любая
                        // синхронизированная серия теряет группировку в
                        // "Продолжить просмотр" (WatchHistoryUseCase.getRecentDeduped
                        // схлопывает по seriesId) и показывается отдельной
                        // строкой на устройстве-получателе, пока оно само не
                        // досмотрит эту серию заново.
                    )
                )
            }

            // Push local changes
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
                prefs.lastSyncTimestamp = 0
                Result.success(Unit)
            } else {
                Result.failure(Exception("Код истёк или неверен"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
