package com.platinum.ott.data.remote

import com.platinum.ott.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ZenithApiService {
    @GET("catalog")
    suspend fun getCatalog(@Query("page") page: Int = 1, @Query("genre") genre: String? = null): CatalogResponseDto
    @GET("movies/{id}")
    suspend fun getMovieById(@Path("id") id: String): MovieDto
    @GET("search")
    suspend fun searchMovies(@Query("q") query: String): List<MovieDto>
    @GET("scripts/manifest")
    suspend fun getScriptManifest(): List<ScriptManifestDto>
    @GET("scripts/{name}")
    suspend fun downloadScript(@Path("name") name: String): Response<ResponseBody>
    @GET("sync")
    suspend fun getSyncData(@Query("since") since: Long): SyncResponseDto
    @POST("sync/push")
    suspend fun pushSyncData(@Body data: SyncPushDto): Response<ResponseBody>
}
