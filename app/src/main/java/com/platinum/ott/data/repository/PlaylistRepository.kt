package com.platinum.ott.data.repository

import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import com.platinum.ott.data.playlist.M3uPlaylistParser
import com.platinum.ott.data.playlist.XtreamVodClient
import com.platinum.ott.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val REFRESH_TTL_MS = 60 * 60 * 1000L // час — плейлисты бывают большие, не гонять на каждый вход на HomeScreen

/**
 * До этого коммита M3U/Xtream-логин был "калиткой" без последствий:
 * validateAndSaveM3U/validateAndSaveXtream (AuthRepositoryImpl) проверяли и
 * сохраняли учётные данные, но ничто в проекте не превращало их в реальный
 * контент — MovieRepositoryImpl обращался только к ZenithApiService. Этот
 * класс закрывает разрыв: парсит M3U или тянет Xtream VOD-список, кэширует
 * в playlist_movies (Room), отдаёт как обычный List<Movie> для HomeViewModel,
 * и резолвит ссылку на поток по id для GetPlayableUrlUseCase.
 */
class PlaylistRepository(
    private val prefs: AuthPreferences,
    private val dao: PlaylistMovieDao,
    private val client: OkHttpClient
) {
    suspend fun getCatalog(forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        if (prefs.type == null) return@withContext emptyList()

        val lastCache = dao.getLatestCacheTime() ?: 0L
        val isStale = System.currentTimeMillis() - lastCache > REFRESH_TTL_MS
        if (forceRefresh || isStale) {
            try {
                refresh()
            } catch (_: Exception) {
                // Плейлист/сервер временно недоступен — отдаём то, что уже
                // есть в локальном кэше, не роняем экран ошибкой. Если кэш
                // тоже пуст — просто пустой список, HomeViewModel это уже
                // трактует как "нечего показать от этого источника", не крах.
            }
        }
        dao.getAll().map { it.toMovie() }
    }

    suspend fun getStreamUrl(movieId: String): String? = withContext(Dispatchers.IO) {
        dao.getById(movieId)?.streamUrl
    }

    private suspend fun refresh() {
        val entries: List<PlaylistMovieEntity> = when (prefs.type) {
            "m3u" -> {
                val url = prefs.m3uUrl ?: return
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
                M3uPlaylistParser.parse(body)
            }
            "xtream" -> {
                val host = prefs.host; val user = prefs.username; val pass = prefs.password
                if (host == null || user == null || pass == null) return
                XtreamVodClient.fetch(client, host, user, pass)
            }
            else -> emptyList()
        }
        // Не трогаем dao.clearAll(), пока не убедились что новые данные
        // реально пришли — иначе временный сетевой сбой посреди refresh()
        // стёр бы уже рабочий кэш и заменил его пустотой.
        if (entries.isNotEmpty()) {
            dao.clearAll()
            dao.upsertAll(entries)
        }
    }
}

private fun PlaylistMovieEntity.toMovie() = Movie(
    id = id, year = year, title = title, poster = poster ?: "",
    genre = genre ?: "Мой плейлист", streamUrl = streamUrl
)
