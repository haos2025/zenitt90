package com.platinum.ott.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovie(@Query("query") query: String, @Query("year") year: Int? = null): TmdbSearchResponse
    @GET("search/tv")
    suspend fun searchTv(@Query("query") query: String): TmdbSearchResponse
    @GET("movie/{id}")
    suspend fun getMovieDetails(@Path("id") id: Int): TmdbMovieDetails
    @GET("tv/{id}")
    suspend fun getTvDetails(@Path("id") id: Int): TmdbTvDetails
    @GET("tv/{id}/next_episode_to_air")
    suspend fun getNextEpisode(@Path("id") id: Int): TmdbNextEpisode
    // PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4 — отдельный запрос вместо
    // append_to_response=credits на getMovieDetails: getMetadata() сейчас
    // работает только с фильмами (searchMovie/getMovieDetails), TV-ветки
    // (/tv/{id}/credits) для детального экрана нет и не заводится в этой
    // сессии — она понадобится только вместе с полноценным TMDB-описанием
    // сериалов, которого сейчас нет вообще (см. SeriesEpisodesViewModel).
    @GET("movie/{id}/credits")
    suspend fun getMovieCredits(@Path("id") id: Int): TmdbCredits
    // PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5 — те же ограничения, что и выше:
    // только фильмы, вариант (б) (без кликабельности) не требует связи с
    // собственным каталогом.
    // PROMPT_DESIGN_SYSTEM.md-сессия про фокус/оверлей/актёров, подзадача 4 —
    // экран актёра с фильмографией. person/{id} — базовые данные (фото,
    // биография, дата/место рождения); combined_credits, а не отдельные
    // movie_credits/tv_credits — TMDB отдаёт фильмографию персоны только так,
    // одним списком с полем media_type ("movie"/"tv") на каждой записи,
    // разделение на секции Фильмы/Сериалы делается на стороне приложения.
    @GET("person/{id}")
    suspend fun getPersonDetails(@Path("id") id: Int): TmdbPersonDetails
    @GET("person/{id}/combined_credits")
    suspend fun getPersonCombinedCredits(@Path("id") id: Int): TmdbPersonCombinedCredits
}

data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())
data class TmdbSearchResult(val id: Int, val title: String?, val name: String?, val release_date: String?, val poster_path: String?, val vote_average: Double?)
// original_language добавлен для авто-определения аниме (PROMPT_FAVORITES_REDESIGN.md,
// п.1) — ISO 639-1 код языка оригинала ("ja" для японского); у эндпоинта
// /movie/{id} именно это поле, не origin_country (оно только у /tv/{id}, а
// getMetadata() работает только с фильмами, см. комментарий в TmdbApiService).
data class TmdbMovieDetails(val id: Int, val title: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val credits: TmdbCredits? = null, val videos: TmdbVideos? = null, val original_language: String? = null)
data class TmdbTvDetails(val id: Int, val name: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val next_episode_to_air: TmdbNextEpisode? = null)
data class TmdbGenre(val id: Int, val name: String)
data class TmdbCredits(val cast: List<TmdbCast> = emptyList())
// profile_path добавлен для карусели актёров (п.4) — раньше credits в
// TmdbMovieDetails всё равно всегда приходил null (append_to_response
// нигде не передавался), так что details.credits?.cast?.take(5) из
// TmdbRepositoryImpl фактически никогда не срабатывал — реального
// поведения это поле раньше не меняло.
// id (person id) добавлен для экрана актёра/фильмографии — без него
// /person/{id} не адресовать вообще; default 0 — совместимость со старыми
// закэшированными записями CastMember в Room (castJson), у которых этого
// поля физически ещё нет (см. CastMember.kt).
data class TmdbCast(val id: Int = 0, val name: String, val character: String?, val profile_path: String? = null)
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
data class TmdbVideo(val key: String, val site: String, val type: String)
data class TmdbNextEpisode(val air_date: String?, val season_number: Int, val episode_number: Int, val name: String?)
data class TmdbRecommendationsResponse(val results: List<TmdbRecommendationItem> = emptyList())
data class TmdbRecommendationItem(val id: Int, val title: String?, val name: String?, val poster_path: String?, val release_date: String?)
// Ни deathday, ни biography TMDB не гарантирует непустыми — оба Nullable,
// экран актёра сам решает, что не показывать при отсутствии данных.
data class TmdbPersonDetails(val id: Int, val name: String, val biography: String?, val birthday: String?, val deathday: String? = null, val place_of_birth: String? = null, val profile_path: String? = null, val known_for_department: String? = null)
data class TmdbPersonCombinedCredits(val cast: List<TmdbPersonCreditItem> = emptyList())
// title — у фильмов, name — у сериалов (TMDB так и отдаёт, в одном списке
// combined_credits, разница только в том, какое из двух полей заполнено);
// release_date/first_air_date — та же пара по тому же принципу.
// media_type ("movie"/"tv") — самое надёжное поле для разделения на секции,
// не наличие title/name (могут теоретически совпадать по формату).
data class TmdbPersonCreditItem(val id: Int, val title: String?, val name: String?, val poster_path: String?, val character: String?, val media_type: String?, val release_date: String?, val first_air_date: String?)
