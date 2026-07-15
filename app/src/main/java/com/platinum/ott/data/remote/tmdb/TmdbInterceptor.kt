package com.platinum.ott.data.remote.tmdb

import com.platinum.ott.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class TmdbInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .addQueryParameter("language", "ru-RU")
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }
}
