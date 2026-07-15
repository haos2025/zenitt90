package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.MetadataDao
import com.platinum.ott.data.local.entity.MetadataEntity
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import com.platinum.ott.domain.model.TmdbMetadata
import com.platinum.ott.domain.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbRepositoryImpl(private val api: TmdbApiService, private val metadataDao: MetadataDao) : TmdbRepository {
    override suspend fun getMetadata(contentId: String, title: String, year: Int?): Result<TmdbMetadata> = withContext(Dispatchers.IO) {
        val cached = metadataDao.getByContentId(contentId)
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < 24 * 3600 * 1000L) {
            return@withContext Result.success(cached.toDomain())
        }
        try {
            val search = api.searchMovie(title, year)
            val result = search.results.firstOrNull() ?: return@withContext Result.failure(Exception("TMDB: не найдено"))
            val details = api.getMovieDetails(result.id)
            val trailer = details.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
            val cast = details.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: ""
            val genres = details.genres.joinToString(", ") { it.name }
            val entity = MetadataEntity(contentId, result.id, details.poster_path, details.backdrop_path,
                details.overview, details.vote_average, genres, trailer?.key?.let { "https://youtube.com/watch?v=$it" }, cast)
            metadataDao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun MetadataEntity.toDomain() = TmdbMetadata(tmdbId, posterPath, backdropPath, overview, voteAverage, genres, trailerUrl, cast)
}
