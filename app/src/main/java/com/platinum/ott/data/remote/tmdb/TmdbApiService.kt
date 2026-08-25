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
    @GET("movie/{id}/recommendations")
    suspend fun getMovieRecommendations(@Path("id") id: Int): TmdbRecommendationsResponse
}

data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())
data class TmdbSearchResult(val id: Int, val title: String?, val name: String?, val release_date: String?, val poster_path: String?, val vote_average: Double?)
data class TmdbMovieDetails(val id: Int, val title: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val credits: TmdbCredits? = null, val videos: TmdbVideos? = null)
data class TmdbTvDetails(val id: Int, val name: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val next_episode_to_air: TmdbNextEpisode? = null)
data class TmdbGenre(val id: Int, val name: String)
data class TmdbCredits(val cast: List<TmdbCast> = emptyList())
// profile_path добавлен для карусели актёров (п.4) — раньше credits в
// TmdbMovieDetails всё равно всегда приходил null (append_to_response
// нигде не передавался), так что details.credits?.cast?.take(5) из
// TmdbRepositoryImpl фактически никогда не срабатывал — реального
// поведения это поле раньше не меняло.
data class TmdbCast(val name: String, val character: String?, val profile_path: String? = null)
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
data class TmdbVideo(val key: String, val site: String, val type: String)
data class TmdbNextEpisode(val air_date: String?, val season_number: Int, val episode_number: Int, val name: String?)
data class TmdbRecommendationsResponse(val results: List<TmdbRecommendationItem> = emptyList())
data class TmdbRecommendationItem(val id: Int, val title: String?, val name: String?, val poster_path: String?, val release_date: String?)
