package com.platinum.ott.data.remote

import com.platinum.ott.BuildConfig
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitFactory {
    // TODO: заменить на реальный адрес после деплоя zenith-backend на Render
    // (см. zenith-backend/README.md, раздел "Деплой на Render.com").
    // 10.0.2.2 — это localhost хост-машины из Android-эмулятора, для
    // локального теста с "uvicorn app.main:app --port 8080" на ПК.
    // Прежний "https://zenith.placeholder.api/" был несуществующим доменом —
    // ZenithApiService не мог получить ответ ни на один запрос.
    private const val ZENITH_BASE_URL = "https://zenith-backend-eviu.onrender.com/"

    fun createOkHttpClient(authPrefs: AuthPreferences, vararg extraInterceptors: Interceptor = emptyArray()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            // RetryInterceptor до логирующего — так HttpLoggingInterceptor
            // логирует каждую отдельную попытку (включая повторы), а не
            // только финальный результат. Раньше сетевого retry не было
            // вообще: любой одиночный таймаут/500 сразу превращался в
            // ошибку на экране, без единой попытки повторить.
            .addInterceptor(RetryInterceptor())
            .addInterceptor(HttpLoggingInterceptor().setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE))
        extraInterceptors.forEach { builder.addInterceptor(it) }
        return builder.build()
    }
    fun createApi(client: OkHttpClient): ZenithApiService =
        Retrofit.Builder().baseUrl(ZENITH_BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ZenithApiService::class.java)
    fun createTmdbApi(client: OkHttpClient): TmdbApiService =
        Retrofit.Builder().baseUrl("https://api.themoviedb.org/3/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(TmdbApiService::class.java)
}
