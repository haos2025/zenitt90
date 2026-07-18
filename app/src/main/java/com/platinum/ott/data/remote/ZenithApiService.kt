package com.platinum.ott.data.remote

import com.platinum.ott.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ZenithApiService {
    @GET("catalog")
    suspend fun getCatalog(@Query("page") page: Int = 1, @Query("genre") genre: String? = null): CatalogResponseDto
    @GET("movie/{id}")
    suspend fun getMovieById(@Path("id") id: String): MovieDto
    @GET("stream/{id}")
    suspend fun getStreamVariants(@Path("id") id: String): List<StreamVariantDto>
    @GET("search")
    suspend fun searchMovies(@Query("q") query: String): List<MovieDto>
    @GET("scripts/manifest.json")
    suspend fun getScriptManifest(): List<ScriptManifestDto>
    @GET("scripts/{name}.js")
    suspend fun downloadScript(@Path("name") name: String): Response<ResponseBody>
    @GET("sync")
    suspend fun getSyncData(@Query("device_id") deviceId: String, @Query("since") since: Long): SyncResponseDto
    @POST("sync/push")
    suspend fun pushSyncData(@Query("device_id") deviceId: String, @Body data: SyncPushDto): Response<ResponseBody>
}
