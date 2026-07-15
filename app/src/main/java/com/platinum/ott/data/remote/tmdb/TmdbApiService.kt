package com.platinum.ott.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovie(@Query("query") query: String, @Query("year") year: Int? = null): TmdbSearchResponse
    @GET("movie/{id}")
    suspend fun getMovieDetails(@Path("id") id: Int): TmdbMovieDetails
    @GET("tv/{id}")
    suspend fun getTvDetails(@Path("id") id: Int): TmdbTvDetails
    @GET("tv/{id}/next_episode_to_air")
    suspend fun getNextEpisode(@Path("id") id: Int): TmdbNextEpisode
}

data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())
data class TmdbSearchResult(val id: Int, val title: String?, val name: String?, val release_date: String?, val poster_path: String?, val vote_average: Double?)
data class TmdbMovieDetails(val id: Int, val title: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val credits: TmdbCredits? = null, val videos: TmdbVideos? = null)
data class TmdbTvDetails(val id: Int, val name: String, val overview: String?, val poster_path: String?, val backdrop_path: String?, val vote_average: Double?, val genres: List<TmdbGenre> = emptyList(), val next_episode_to_air: TmdbNextEpisode? = null)
data class TmdbGenre(val id: Int, val name: String)
data class TmdbCredits(val cast: List<TmdbCast> = emptyList())
data class TmdbCast(val name: String, val character: String?)
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
data class TmdbVideo(val key: String, val site: String, val type: String)
data class TmdbNextEpisode(val air_date: String?, val season_number: Int, val episode_number: Int, val name: String?)
