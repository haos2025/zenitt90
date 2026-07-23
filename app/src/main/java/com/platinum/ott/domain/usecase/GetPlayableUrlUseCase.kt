package com.platinum.ott.domain.usecase

import com.google.gson.Gson
import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.StreamVariantDto
import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Три независимых, все рабочих пути получения ссылки на видео — выбор по
 * префиксу ID:
 *
 *  1. "yt_"/"ia_" (контент из Zenith backend) — ГИБРИДНАЯ МОДЕЛЬ (Задача 2,
 *     2026-07-21): backend и все включённые JS-плагины опрашиваются
 *     ПАРАЛЛЕЛЬНО, у каждого плагина свой таймаут — сбой/зависание одного
 *     не блокирует ни backend-результат, ни остальные плагины. Это касается
 *     ТОЛЬКО момента воспроизведения конкретного уже выбранного фильма —
 *     каталог/браузинг (HomeViewModel) по-прежнему ведёт только backend,
 *     плагины там не участвуют вообще (см. ARCHITECTURE_DECISIONS.md —
 *     параллельный опрос N плагинов на каждую отрисовку каталога был бы
 *     той самой болячкой Lampa, которую решили не повторять).
 *
 *  2. "m3u_"/"xt_" (контент из собственного M3U/Xtream-плейлиста
 *     пользователя) — ссылка уже известна из парсинга плейлиста/Xtream API,
 *     второй сетевой запрос не нужен вообще.
 *
 *  3. Любой другой ID (контент, добавленный через ScriptProvider —
 *     ОТДЕЛЬНЫЙ от PluginManager механизм, один встроенный "parser"-скрипт,
 *     не путать с гонкой по установленным плагинам из пункта 1).
 */
class GetPlayableUrlUseCase(
    private val scriptProvider: ScriptProvider,
    private val api: ZenithApiService,
    private val playlistRepository: PlaylistRepository,
    private val pluginManager: PluginManager,
    private val getMovie: GetMovieByIdUseCase,
    private val authRepo: AuthRepository
) {
    companion object {
        private val ZENITH_BACKEND_PREFIXES = setOf("yt", "ia")
        private val PLAYLIST_PREFIXES = setOf("m3u", "xt")
        private const val PARSER_SCRIPT_NAME = "player_parser" // без .js — см. ScriptProvider.getScript
        private const val PARSER_FUNCTION_NAME = "parseMovie"

        // Контракт для JS-плагина в роли "резерва при воспроизведении"
        // (формализовано в Задаче 2): findStream(title, year) — плагин ищет
        // по названию/году, не по внутреннему id backend (yt_xxx плагину
        // ничего не говорит). Если плагин её не экспортирует —
        // PluginManager.callPluginFunction() поймает ReferenceError внутри
        // QuickJS и вернёт null — просто не участвует в гонке, не ошибка.
        private const val PLUGIN_FUNCTION_NAME = "findStream"
        private const val PLUGIN_RACE_TIMEOUT_MS = 4000L
    }

    private val gson = Gson()

    suspend fun execute(movieId: String): List<StreamVariant> = withContext(Dispatchers.IO) {
        val prefix = movieId.substringBefore('_', missingDelimiterValue = "")
        when {
            prefix in ZENITH_BACKEND_PREFIXES -> executeWithPluginRace(movieId)
            prefix in PLAYLIST_PREFIXES -> {
                val url = playlistRepository.getStreamUrl(movieId)
                if (url != null) listOf(StreamVariant("Оригинал", url, source = "Мой плейлист")) else emptyList()
            }
            else -> {
                try {
                    val result = scriptProvider.evaluateScript(PARSER_SCRIPT_NAME, PARSER_FUNCTION_NAME, movieId)
                        ?: return@withContext emptyList()
                    val parsed = gson.fromJson(result, Array<StreamVariantDto>::class.java) ?: emptyArray()
                    parsed.map { StreamVariant(it.quality, it.url, source = "Плагин") }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    /**
     * Backend и включённые плагины опрашиваются через async{} параллельно
     * друг другу — общее время ожидания ограничено максимумом из времени
     * backend-ответа и PLUGIN_RACE_TIMEOUT_MS, НЕ суммой (плагины между
     * собой тоже параллельны, не по очереди).
     */
    private suspend fun executeWithPluginRace(movieId: String): List<StreamVariant> = coroutineScope {
        val backendDeferred = async {
            try {
                api.getStreamVariants(movieId).map { StreamVariant(it.quality, it.url, source = "Zenith") }
            } catch (_: Exception) {
                emptyList()
            }
        }

        val pluginsDeferred = async {
            val movie = getMovie.execute(movieId).getOrNull() ?: return@async emptyList()
            val enabledPlugins = try {
                pluginManager.getEnabledPlugins().first()
            } catch (_: Exception) {
                emptyList()
            }
            if (enabledPlugins.isEmpty()) return@async emptyList()

            enabledPlugins.map { plugin ->
                async {
                    try {
                        withTimeoutOrNull(PLUGIN_RACE_TIMEOUT_MS) {
                            val result = pluginManager.callPluginFunction(
                                plugin.id, PLUGIN_FUNCTION_NAME, movie.title, movie.year.toString()
                            ) ?: return@withTimeoutOrNull emptyList()
                            val parsed = gson.fromJson(result, Array<StreamVariantDto>::class.java) ?: emptyArray()
                            parsed.map { StreamVariant(it.quality, it.url, source = plugin.name) }
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        backendDeferred.await() + pluginsDeferred.await()
    }
}
