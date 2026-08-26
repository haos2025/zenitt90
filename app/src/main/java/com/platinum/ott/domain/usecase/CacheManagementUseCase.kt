package com.platinum.ott.domain.usecase

import android.content.Context
import coil.imageLoader
import com.platinum.ott.data.local.dao.MetadataDao
import com.platinum.ott.data.local.dao.MovieDao
import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.domain.model.CacheOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Заменяет прежний ClearCacheUseCase (чистил только MovieDao, посмотреть
 * размер было нельзя вообще). Экран управления кэшем — PROMPT_CACHE_MANAGEMENT.md.
 *
 * Что считается кэшем (сюда входит):
 * - Каталог с бэкенда — MovieDao (Room, TTL 10 мин)
 * - Плейлист M3U/Xtream — PlaylistMovieDao (Room, TTL 1 час)
 * - TMDB-метаданные — MetadataDao (Room, TTL 24 часа)
 * - Постеры-картинки — Coil ImageLoader, диск (poster_cache/, лимит 250 МБ)
 * - Краш-логи — filesDir/crash_logs/ (не кэш по смыслу, но тот же случай
 *   "растущий мусор, который можно почистить")
 *
 * Что НЕ входит сознательно (см. промт): избранное и история просмотра,
 * установленные плагины (PluginEntity.scriptContent — стирание без
 * гарантии перекачки, если repoUrl к тому моменту отвалится), настройки и
 * учётные данные. Скачанные JS-парсеры (ScriptProvider) в промте тоже не
 * значились как отдельная категория — из этого экрана не управляются,
 * ScriptProvider.clearAll() остаётся отдельным механизмом (OTA-обновление
 * парсеров на экране Настроек), не трогаем.
 */
class CacheManagementUseCase(
    private val context: Context,
    private val movieDao: MovieDao,
    private val playlistMovieDao: PlaylistMovieDao,
    private val metadataDao: MetadataDao
) {
    private val crashLogDir = File(context.filesDir, "crash_logs")

    suspend fun getOverview(): CacheOverview = withContext(Dispatchers.IO) {
        val crashFiles = crashLogDir.listFiles().orEmpty()
        CacheOverview(
            catalogEntries = movieDao.getTotalCount(),
            playlistEntries = playlistMovieDao.getCount(),
            metadataEntries = metadataDao.getCount(),
            // DiskCache.size — реальный текущий размер на диске в Coil 2.x,
            // не оценка. null, только если ImageLoader вообще без диск-кэша
            // (не наш случай — см. ZenithApplication.newImageLoader()).
            posterCacheBytes = context.imageLoader.diskCache?.size ?: 0L,
            crashLogBytes = crashFiles.sumOf { it.length() },
            crashLogCount = crashFiles.size
        )
    }

    suspend fun clearCatalog(): Unit = withContext(Dispatchers.IO) { movieDao.clearAll() }
    suspend fun clearPlaylist(): Unit = withContext(Dispatchers.IO) { playlistMovieDao.clearAll() }
    suspend fun clearMetadata(): Unit = withContext(Dispatchers.IO) { metadataDao.clearAll() }
    suspend fun clearPosters(): Unit = withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }
    suspend fun clearCrashLogs(): Unit = withContext(Dispatchers.IO) {
        crashLogDir.listFiles()?.forEach { it.delete() }
    }
}
