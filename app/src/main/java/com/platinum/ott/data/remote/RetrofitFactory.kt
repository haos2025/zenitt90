package com.platinum.ott.data.remote

import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitFactory {
    fun createOkHttpClient(authPrefs: AuthPreferences, vararg extraInterceptors: Interceptor = emptyArray()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        extraInterceptors.forEach { builder.addInterceptor(it) }
        return builder.build()
    }
    fun createApi(client: OkHttpClient): ZenithApiService =
        Retrofit.Builder().baseUrl("https://zenith.placeholder.api/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ZenithApiService::class.java)
    fun createTmdbApi(client: OkHttpClient): TmdbApiService =
        Retrofit.Builder().baseUrl("https://api.themoviedb.org/3/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(TmdbApiService::class.java)
}
