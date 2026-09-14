package com.platinum.ott.data.remote

import com.platinum.ott.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    @POST("sync/pairing/create")
    suspend fun createPairingCode(@Query("device_id") deviceId: String): PairingCreateDto
    @POST("sync/pairing/redeem")
    suspend fun redeemPairingCode(@Query("device_id") deviceId: String, @Body body: PairingRedeemDto): Response<ResponseBody>
    // PROMPT_SUBTITLES.md, подзадача 1 — лёгкая проверка OpenSubtitles для
    // VOD, по названию/году (не по хешу — контент стримится, а не
    // скачивается файлом, матчинг неидеален, особенно для YouTube/
    // Archive.org-каталога с неофициальными названиями). Ключ OpenSubtitles
    // остаётся только на zenith-backend, сюда не попадает — тот же принцип,
    // что уже применён к YOUTUBE_API_KEY (см. README.md/COMPATIBILITY.md).
    // ВАЖНО: сам эндпоинт на стороне zenith-backend (отдельный репозиторий)
    // ещё предстоит реализовать отдельной сессией — этот вызов уже
    // соответствует итоговому контракту, но пока будет получать 404, пока
    // backend не обновлён.
    @GET("subtitles/opensubtitles")
    suspend fun searchOpenSubtitles(
        @Query("title") title: String,
        @Query("year") year: Int,
        @Query("langs") langs: String = "ru,en"
    ): List<OpenSubtitlesMatchDto>

    // PROMPT_SUBTITLES.md, подзадача 6 — облачный STT, реализован на
    // backend в подзадаче 3. Аудио — уже нарезанный VAD-сегмент речи в WAV
    // (не весь чанк целиком), см. SubtitleOrchestrator.kt/WavEncoder.kt.
    @Multipart
    @POST("subtitles/transcribe")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @Part("language") language: RequestBody?
    ): SttTranscriptionDto
}
