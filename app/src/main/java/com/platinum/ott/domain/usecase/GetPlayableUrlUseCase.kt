package com.platinum.ott.domain.usecase

import com.google.gson.Gson
import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.StreamVariantDto
import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Три независимых, все рабочих пути получения ссылки на видео — выбор по
 * префиксу ID:
 *
 *  1. "yt_"/"ia_" (контент из Zenith backend, YouTube/Archive.org плагины
 *     на Python-стороне) — прямой REST-вызов GET /stream/{id}, минуя
 *     QuickJS полностью.
 *
 *  2. "m3u_"/"xt_" (контент из собственного M3U/Xtream-плейлиста
 *     пользователя) — ссылка уже известна из парсинга плейлиста/Xtream API
 *     (см. PlaylistRepository), второй сетевой запрос не нужен вообще,
 *     просто читаем из локального кэша Room по id.
 *
 *  3. Любой другой ID (контент, добавленный через установленный
 *     пользователем JS-плагин) — через ScriptProvider + локальный
 *     "player_parser"-скрипт (player_parser.js, скачивается через
 *     /scripts/manifest.json, см. OtaUpdateUseCase).
 *
 *     Раньше здесь было ТРИ независимых расхождения, из-за которых этот
 *     путь никогда не срабатывал, даже когда backend/QuickJS-мост были
 *     полностью рабочими:
 *       - вызывался evaluateScript("parser", ...) — искался файл
 *         scripts/parser.js, а OtaUpdateUseCase реально сохраняет файл
 *         как scripts/player_parser.js (имя берётся из SCRIPT_VERSIONS
 *         на backend, см. app/routers/scripts.py) — файл не находился.
 *       - вызывалась функция "getStreams" — а player_parser.js определяет
 *         только parseMovie(movieId) — ReferenceError внутри QuickJS.
 *       - результат парсился как "quality|url;quality|url" (сплит по ';'
 *         и '|') — а parseMovie() возвращает JSON-массив вида
 *         [{"quality":"...","url":"..."}] — форматы несовместимы.
 *     Все три исправлены здесь разом.
 */
class GetPlayableUrlUseCase(
    private val scriptProvider: ScriptProvider,
    private val api: ZenithApiService,
    private val playlistRepository: PlaylistRepository,
    private val authRepo: AuthRepository
) {
    companion object {
        private val ZENITH_BACKEND_PREFIXES = setOf("yt", "ia")
        private val PLAYLIST_PREFIXES = setOf("m3u", "xt")
        private const val PARSER_SCRIPT_NAME = "player_parser" // без .js — см. ScriptProvider.getScript
        private const val PARSER_FUNCTION_NAME = "parseMovie"
    }

    private val gson = Gson()

    suspend fun execute(movieId: String): List<StreamVariant> = withContext(Dispatchers.IO) {
        val prefix = movieId.substringBefore('_', missingDelimiterValue = "")
        when {
            prefix in ZENITH_BACKEND_PREFIXES -> {
                try {
                    api.getStreamVariants(movieId).map { StreamVariant(it.quality, it.url) }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            prefix in PLAYLIST_PREFIXES -> {
                val url = playlistRepository.getStreamUrl(movieId)
                if (url != null) listOf(StreamVariant("Оригинал", url)) else emptyList()
            }
            else -> {
                try {
                    val result = scriptProvider.evaluateScript(PARSER_SCRIPT_NAME, PARSER_FUNCTION_NAME, movieId)
                        ?: return@withContext emptyList()
                    val parsed = gson.fromJson(result, Array<StreamVariantDto>::class.java) ?: emptyArray()
                    parsed.map { StreamVariant(it.quality, it.url) }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }
}
