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

class MovieRepositoryImpl(private val api: ZenithApiService, private val dao: MovieDao) : MovieRepository {
    private val cacheMutex = Mutex()
    private val CACHE_TTL = 10 * 60 * 1000L

    override suspend fun getCatalog(page: Int, genre: String?): Result<CatalogPage> = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val cached = dao.getPage(20, (page - 1) * 20)
                val total = dao.getTotalCount()
                val cacheTime = dao.getLatestCacheTime() ?: 0
                if (cached.isNotEmpty() && System.currentTimeMillis() - cacheTime < CACHE_TTL) {
                    return@withContext Result.success(cached.map { it.toDomain() }.toPage(page, total))
                }
                val resp = api.getCatalog(page, genre)
                val entities = resp.items.map { it.toEntity() }
                if (page == 1) dao.clearAll()
                dao.upsertAll(entities)
                Result.success(entities.map { it.toDomain() }.toPage(page, resp.totalItems))
            } catch (e: Exception) {
                val cached = dao.getPage(20, (page - 1) * 20)
                if (cached.isNotEmpty()) Result.success(cached.map { it.toDomain() }.toPage(page, dao.getTotalCount()))
                else Result.failure(e)
            }
        }
    }

    override suspend fun getMovieById(id: String): Result<Movie> = withContext(Dispatchers.IO) {
        try { Result.success(api.getMovieById(id).toDomain().also { dao.upsert(it.toEntity()) }) }
        catch (e: Exception) { dao.getById(id)?.let { Result.success(it.toDomain()) } ?: Result.failure(e) }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try { Result.success(api.searchMovies(query).map { it.toDomain() }) }
        catch (e: Exception) { val c = dao.search(query); if (c.isNotEmpty()) Result.success(c.map { it.toDomain() }) else Result.failure(e) }
    }

    private fun List<Movie>.toPage(page: Int, total: Int) = CatalogPage(this, page, (total + 19) / 20, total)
}
