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

    // Раньше SeriesTrackerUseCase.updateSchedule() был заглушкой — весь этот
    // путь (поиск сериала → следующая серия → дата выхода) нигде не был
    // реализован. air_date у TMDB приходит как "yyyy-MM-dd" без времени —
    // берём начало дня по UTC, для планирования уведомления точность до часа
    // не нужна.
    override suspend fun getNextEpisode(seriesTitle: String): com.platinum.ott.domain.model.NextEpisode? = withContext(Dispatchers.IO) {
        try {
            val found = api.searchTv(seriesTitle).results.firstOrNull() ?: return@withContext null
            val next = api.getNextEpisode(found.id)
            val airDate = next.air_date ?: return@withContext null
            val epochMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(airDate)?.time ?: return@withContext null
            com.platinum.ott.domain.model.NextEpisode(epochMs, next.season_number, next.episode_number, next.name ?: "")
        } catch (e: Exception) { null }
    }
}
