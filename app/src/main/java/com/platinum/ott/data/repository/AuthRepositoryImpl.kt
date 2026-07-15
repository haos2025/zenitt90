package com.platinum.ott.data.repository

import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthRepositoryImpl(private val prefs: AuthPreferences, private val client: OkHttpClient) : AuthRepository {
    override fun isLoggedIn() = prefs.isLoggedIn()
    override fun getAuthType() = prefs.type

    override suspend fun validateAndSaveM3U(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                .build().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    val body = resp.body?.string() ?: ""
                    if (!body.contains("#EXTINF")) return@withContext Result.failure(Exception("Не M3U-плейлист"))
                }
            prefs.type = "m3u"; prefs.m3uUrl = url; Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun validateAndSaveXtream(host: String, username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${host.trimEnd('/')}/player_api.php?username=$username&password=$password"
            val req = Request.Builder().url(url).build()
            client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                .build().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                }
            prefs.type = "xtream"; prefs.host = host; prefs.username = username; prefs.password = password
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun logout() { prefs.clear() }
}
