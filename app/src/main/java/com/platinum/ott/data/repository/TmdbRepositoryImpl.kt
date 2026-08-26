package com.platinum.ott.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.platinum.ott.data.local.dao.MetadataDao
import com.platinum.ott.data.local.entity.MetadataEntity
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import com.platinum.ott.domain.model.CastMember
import com.platinum.ott.domain.model.Recommendation
import com.platinum.ott.domain.model.TmdbMetadata
import com.platinum.ott.domain.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException

// Раньше не было НИ ограничения на число одновременных запросов к TMDB, НИ
// повторных попыток при 429 — не было проблемой, пока TMDB дёргался только
// с экрана деталки (один фильм за раз). После ROADMAP.md п.11 (постеры в
// сетке каталога) каждая видимая карточка запускает СВОЙ вызов
// getMetadata() почти одновременно — на одном экране Home легко 20-30
// карточек сразу near-viewport → 20-30 параллельных запросов разом, TMDB
// начинает отвечать 429 (лимит free-плана — про запросы в короткий
// промежуток времени, не про общий объём). Result.failure() от 429 нигде
// не отличался от "фильм не найден" — DetailViewModel.load() в обоих
// случаях просто падает на сырой movie.poster (см. комментарий там же).
// Это и есть репортнутый баг "постер в каталоге есть, в детальной —
// случайный скриншот": сама детальная карточка могла попасть под раздачу
// 429 от того, что каталог только что отстрелял пачку запросов.
private val tmdbConcurrencyLimiter = Semaphore(permits = 4)
private val gson = Gson()
private val castListType = object : TypeToken<List<CastMember>>() {}.type

class TmdbRepositoryImpl(private val api: TmdbApiService, private val metadataDao: MetadataDao) : TmdbRepository {
    override suspend fun getMetadata(contentId: String, title: String, year: Int?): Result<TmdbMetadata> = withContext(Dispatchers.IO) {
        val cached = metadataDao.getByContentId(contentId)
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < 24 * 3600 * 1000L) {
            return@withContext Result.success(cached.toDomain())
        }
        try {
            // withPermit — не более 4 одновременных запросов к TMDB на всё
            // приложение разом (общий Semaphore на уровне файла, не per-
            // instance — TmdbRepositoryImpl создаётся один раз в
            // SessionGraph, но общий объект чуть надёжнее, если это
            // когда-нибудь изменится).
            val entity = tmdbConcurrencyLimiter.withPermit { fetchWithRetry(contentId, title, year) }
            metadataDao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (e: Exception) { Result.failure(e) }
    }

    // До 2 повторных попыток именно на 429 (Too Many Requests) с паузой —
    // на любую другую ошибку (404/сеть/таймаут) повтор не имеет смысла,
    // ситуация не изменится за секунду. Кредиты (PROMPT_DETAIL_SCREEN_UPGRADE.md,
    // п.4) запрашиваются отдельным вызовом внутри того же retry — если 429
    // словит именно он, а не getMovieDetails, вся попытка просто повторяется
    // целиком, отдельный счётчик под кредиты не заводится (лишняя сложность
    // ради события, которое и так редко — getMetadata уже под общим лимитом
    // в 4 параллельных запроса).
    private suspend fun fetchWithRetry(contentId: String, title: String, year: Int?, attempt: Int = 0): MetadataEntity {
        try {
            val search = api.searchMovie(title, year)
            val result = search.results.firstOrNull() ?: throw Exception("TMDB: не найдено")
            val details = api.getMovieDetails(result.id)
            val trailer = details.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
            val genres = details.genres.joinToString(", ") { it.name }
            val castMembers = try {
                api.getMovieCredits(result.id).cast.take(15)
                    .map { CastMember(it.name, it.character?.takeIf { c -> c.isNotBlank() }, it.profile_path) }
            } catch (e: Exception) { emptyList() } // карусель актёров — второстепенная деталь, не должна ронять всю карточку фильма
            val castJson = if (castMembers.isNotEmpty()) gson.toJson(castMembers) else null
            return MetadataEntity(contentId, result.id, details.poster_path, details.backdrop_path,
                details.overview, details.vote_average, genres, trailer?.key?.let { "https://youtube.com/watch?v=$it" },
                castJson = castJson, originalLanguage = details.original_language)
        } catch (e: HttpException) {
            if (e.code() == 429 && attempt < 2) {
                delay(800L * (attempt + 1)) // 800мс, потом 1600мс — TMDB отдаёт Retry-After, но не все версии okhttp/retrofit прокидывают заголовок сюда без доп. интерцептора, литеральная пауза надёжнее без лишней связности
                return fetchWithRetry(contentId, title, year, attempt + 1)
            }
            throw e
        }
    }

    private fun MetadataEntity.toDomain(): TmdbMetadata {
        val cast = castJson?.let { json ->
            try { gson.fromJson<List<CastMember>>(json, castListType) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        return TmdbMetadata(tmdbId, posterPath, backdropPath, overview, voteAverage, genres, trailerUrl, cast, originalLanguage)
    }

    // PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5 — вариант (б): чистая витрина,
    // без сопоставления с собственным каталогом и без кэша в Room (см.
    // комментарий в TmdbRepository.kt). Любая ошибка — пустой список,
    // экран деталки просто не рисует блок, а не показывает ошибку поверх
    // уже загруженного фильма.
    override suspend fun getRecommendations(tmdbId: Int): List<Recommendation> = withContext(Dispatchers.IO) {
        try {
            tmdbConcurrencyLimiter.withPermit {
                api.getMovieRecommendations(tmdbId).results.mapNotNull { item ->
                    val title = item.title ?: item.name ?: return@mapNotNull null
                    val year = item.release_date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
                    Recommendation(item.id, title, item.poster_path, year)
                }
            }
        } catch (e: Exception) { emptyList() }
    }

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
