package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import com.platinum.ott.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * До задачи "Источники" (PROMPT_SOURCES_SCREEN.md) этот класс сам читал
 * AuthPreferences и сам фетчил M3U/Xtream — единственный источник. Теперь
 * фетч и хранение конфига источников — PlaylistSourceRepository (соседний
 * файл), этот класс отвечает только за агрегацию уже закэшированного
 * контента ВСЕХ включённых источников в единый List<Movie>. Публичный
 * интерфейс наружу (getCatalog/getMovieById/getSeriesList/
 * getEpisodesForSeries/getStreamInfo) не изменился — вызывающий код
 * (HomeViewModel через GetPlaylistCatalogUseCase, SeriesViewModels,
 * PlayerViewModel, MovieRepositoryImpl, GetPlayableUrlUseCase) не тронут.
 */
class PlaylistRepository(
    private val sourceDao: PlaylistSourceDao,
    private val movieDao: PlaylistMovieDao,
    private val sourceRepository: PlaylistSourceRepository
) {
    suspend fun getCatalog(forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        val enabledSources = sourceDao.getEnabled() // уже ORDER BY priority ASC
        if (enabledSources.isEmpty()) return@withContext emptyList()

        // Обновляем каждый источник отдельно (свой TTL/URL/тип). Ошибка
        // одного источника не должна ронять остальные — refresh() сам ловит
        // исключение и пишет lastRefreshStatus, здесь ловить нечего.
        enabledSources.forEach { source -> sourceRepository.refresh(source.id, forceRefresh = forceRefresh) }

        val entities = movieDao.getAllForSources(enabledSources.map { it.id })

        // Дедуп по названию+год (решение сессии) — полный дедуп каналов для
        // будущего IPTV-направления сюда не относится, это только обычный
        // VOD-каталог. При совпадении title+year побеждает запись из
        // источника с более высоким приоритетом (enabledSources уже
        // отсортирован по priority). Строки в БД не удаляются — дедуп
        // только на чтении, поэтому смена приоритета/включения источников
        // сразу меняет результат следующего getCatalog() без перекачки.
        val priorityOf = enabledSources.withIndex().associate { (index, s) -> s.id to index }
        entities
            .sortedBy { priorityOf[it.sourceId] ?: Int.MAX_VALUE }
            .distinctBy { it.title.trim().lowercase() to it.year }
            .map { it.toMovie() }
    }

    suspend fun getMovieById(id: String): Movie? = withContext(Dispatchers.IO) {
        movieDao.getById(id)?.toMovie()
    }

    suspend fun getSeriesList(): List<SeriesSummary> = withContext(Dispatchers.IO) {
        val enabledIds = sourceDao.getEnabled().map { it.id }
        movieDao.getAllForSources(enabledIds)
            .filter { it.seriesId != null }
            .groupBy { it.seriesId!! }
            .map { (seriesId, episodes) ->
                val first = episodes.first()
                SeriesSummary(
                    seriesId = seriesId,
                    title = first.seriesTitle ?: first.title,
                    poster = first.poster ?: "",
                    genre = first.genre ?: "Мой плейлист",
                    episodeCount = episodes.size
                )
            }
            .sortedBy { it.title }
    }

    suspend fun getEpisodesForSeries(seriesId: String): List<Movie> = withContext(Dispatchers.IO) {
        // Прямой запрос по seriesId (не через getAllForSources) — сериал
        // уже выбран конкретный откуда-то (например, из истории), включён
        // ли сейчас его источник — не так важно для этого метода; дедуп по
        // названию+год тут неприменим (это эпизоды одного сериала, не
        // разные фильмы).
        movieDao.getAll()
            .filter { it.seriesId == seriesId }
            .sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
            .map { it.toMovie() }
    }

    suspend fun getStreamInfo(movieId: String): PlaylistStreamInfo? = withContext(Dispatchers.IO) {
        val entity = movieDao.getById(movieId) ?: return@withContext null
        val headers = buildMap {
            entity.userAgent?.let { put("User-Agent", it) }
            entity.referrer?.let { put("Referer", it) }
        }
        PlaylistStreamInfo(entity.streamUrl, headers)
    }
}

private fun PlaylistMovieEntity.toMovie() = Movie(
    id = id, year = year, title = title, poster = poster ?: "",
    genre = genre ?: "Мой плейлист", streamUrl = streamUrl,
    seriesId = seriesId, seriesTitle = seriesTitle, seasonNumber = seasonNumber, episodeNumber = episodeNumber
)

data class PlaylistStreamInfo(val url: String, val headers: Map<String, String>)
data class SeriesSummary(val seriesId: String, val title: String, val poster: String, val genre: String, val episodeCount: Int)
