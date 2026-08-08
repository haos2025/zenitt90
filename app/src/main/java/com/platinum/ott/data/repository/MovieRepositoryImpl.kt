package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.MovieDao
import com.platinum.ott.data.local.mapper.EntityMapper.toDomain
import com.platinum.ott.data.local.mapper.EntityMapper.toEntity
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.mapper.MovieMapper.toDomain
import com.platinum.ott.data.remote.mapper.MovieMapper.toEntity
import com.platinum.ott.domain.model.CatalogPage
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MovieRepositoryImpl(
    private val api: ZenithApiService,
    private val dao: MovieDao,
    private val playlistRepository: PlaylistRepository
) : MovieRepository {
    private val cacheMutex = Mutex()
    private val CACHE_TTL = 10 * 60 * 1000L

    // Раньше totalPages в CatalogPage считался из resp.totalItems — а это
    // поле backend (CatalogResponseOut) физически НЕ отдаёт, оно всегда
    // дефолтное 0. (0 + 19) / 20 = 0, а HomeViewModel.loadMore() пропускает
    // подгрузку, если page >= totalPages — 1 >= 0 всегда true. Поэтому
    // "лента" молча никогда не грузила вторую страницу, сколько ни держи
    // скролл, хотя backend честно отдаёт total_pages (это ДРУГОЕ поле в том
    // же ответе, оно приходит правильно, просто не читалось). Держим
    // последнее известное значение в памяти на случай раздачи из кэша
    // (Room не хранит totalPages как метаданные страницы).
    private var lastKnownTotalPages: Int = 1

    override suspend fun getCatalog(page: Int, genre: String?): Result<CatalogPage> = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val cached = dao.getPage(20, (page - 1) * 20)
                val total = dao.getTotalCount()
                val cacheTime = dao.getLatestCacheTime() ?: 0
                if (cached.isNotEmpty() && System.currentTimeMillis() - cacheTime < CACHE_TTL) {
                    return@withContext Result.success(cached.map { it.toDomain() }.toPage(page, lastKnownTotalPages, total))
                }
                val resp = api.getCatalog(page, genre)
                val entities = resp.items.map { it.toEntity() }
                if (page == 1) dao.clearAll()
                dao.upsertAll(entities)
                lastKnownTotalPages = resp.totalPages
                Result.success(entities.map { it.toDomain() }.toPage(page, resp.totalPages, resp.totalItems))
            } catch (e: Exception) {
                val cached = dao.getPage(20, (page - 1) * 20)
                if (cached.isNotEmpty()) Result.success(cached.map { it.toDomain() }.toPage(page, lastKnownTotalPages, dao.getTotalCount()))
                else Result.failure(e)
            }
        }
    }

    /**
     * Раньше ЛЮБОЙ id, включая контент из собственного M3U/Xtream-плейлиста
     * пользователя, безусловно уходил в api.getMovieById() — backend вообще
     * не знает про префиксы "m3u"/"xt" (registry.resolve_content_id
     * распознаёт только "yt"/"ia"), поэтому экран деталей для ЛЮБОГО канала
     * плейлиста ловил настоящий HTTP 404 от backend, даже не доходя до
     * плеера — ни один фикс плеера не мог сработать, до него не добирались.
     */
    override suspend fun getMovieById(id: String): Result<Movie> = withContext(Dispatchers.IO) {
        val prefix = id.substringBefore('_', missingDelimiterValue = "")
        if (prefix == "m3u" || prefix == "xt") {
            val fromPlaylist = try { playlistRepository.getMovieById(id) } catch (_: Exception) { null }
            return@withContext fromPlaylist?.let { Result.success(it) }
                ?: Result.failure(Exception("Канал не найден в плейлисте"))
        }
        try { Result.success(api.getMovieById(id).toDomain().also { dao.upsert(it.toEntity()) }) }
        catch (e: Exception) { dao.getById(id)?.let { Result.success(it.toDomain()) } ?: Result.failure(e) }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try { Result.success(api.searchMovies(query).map { it.toDomain() }) }
        catch (e: Exception) { val c = dao.search(query); if (c.isNotEmpty()) Result.success(c.map { it.toDomain() }) else Result.failure(e) }
    }

    private fun List<Movie>.toPage(page: Int, totalPages: Int, totalItems: Int) = CatalogPage(this, page, totalPages.coerceAtLeast(1), totalItems)
}
