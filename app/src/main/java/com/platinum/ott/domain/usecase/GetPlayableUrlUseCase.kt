package com.platinum.ott.domain.usecase

import com.google.gson.Gson
import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.data.local.dao.ChannelDao
import com.platinum.ott.data.local.dao.ChannelStreamDao
import com.platinum.ott.data.local.entity.ChannelStreamEntity
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.playlist.CatchupUrlBuilder
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.StreamVariantDto
import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.domain.model.StreamVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Четыре независимых, все рабочих пути получения ссылки на видео — выбор по
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
 *     пользователя, VOD) — ссылка уже известна из парсинга плейлиста/Xtream
 *     API, второй сетевой запрос не нужен вообще.
 *
 *  3. "ch_" (живой канал, PROMPT_IPTV_FOUNDATION.md, подзадача "fallback
 *     при воспроизведении") — Channel.id как таковой не хранит ссылку,
 *     ссылок несколько (по одной на источник, ChannelStreamEntity).
 *     Стратегия: НЕ пробовать их по очереди самим (это добавило бы
 *     задержку перед стартом воспроизведения на каждый мёртвый источник) —
 *     вернуть ВСЕ как отдельные StreamVariant, отсортированные так, чтобы
 *     первый (currentVariant по умолчанию в PlayerViewModel.loadMovie())
 *     был самым вероятным живым по уже накопленным health-check данным
 *     (ChannelHealthChecker). Реальная гонка/переключение при отказе — уже
 *     готовый механизм PlayerViewModel: на ERROR_CODE_IO_* он сам берёт
 *     variants.getOrNull(currentIndex + 1) и пробует следующий — тот же
 *     код, что уже переключает 1080p→720p при сбое качества, здесь просто
 *     "следующий вариант" оказывается другим источником того же канала,
 *     не другим качеством. Продолжение уже выбранного подхода, не с нуля.
 *
 *  4. Любой другой ID (контент, добавленный через ScriptProvider —
 *     ОТДЕЛЬНЫЙ от PluginManager механизм, один встроенный "parser"-скрипт,
 *     не путать с гонкой по установленным плагинам из пункта 1).
 */
/**
 * PROMPT_EPG.md, подзадача 5 — если передан execute(), значит запрошено
 * воспроизведение АРХИВА конкретной программы, а не прямого эфира; окно
 * должно совпадать с временем самой программы (см. EpgGridViewModel/
 * EpgGridScreen — там же и решается, какие программы вообще кликабельны
 * для этого).
 */
data class CatchupWindow(val startMillis: Long, val endMillis: Long)

class GetPlayableUrlUseCase(
    private val scriptProvider: ScriptProvider,
    private val api: ZenithApiService,
    private val playlistRepository: PlaylistRepository,
    private val pluginManager: PluginManager,
    private val getMovie: GetMovieByIdUseCase,
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao,
    // PROMPT_EPG.md, подзадача 5 — нужен только для catchup-ветки (host/
    // username/password источника, см. executeChannelCatchup()); live-ветка
    // (executeChannelFallback()) его не трогает, ChannelStreamEntity.streamUrl
    // и так уже полный.
    private val playlistSourceDao: PlaylistSourceDao
) {
    companion object {
        private val ZENITH_BACKEND_PREFIXES = setOf("yt", "ia")
        private val PLAYLIST_PREFIXES = setOf("m3u", "xt")
        private const val CHANNEL_PREFIX = "ch_"
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

        // Сортировка ChannelStream при fallback — см. executeChannelFallback().
        // "dead" не исключается совсем: если ВСЕ стримы канала мертвы,
        // лучше дать PlayerViewModel честно попробовать и показать ошибку,
        // чем заранее решить за пользователя "тут вообще нечего показывать".
        private val CHANNEL_STATUS_RANK = mapOf("alive" to 0, "unknown" to 1, "dead" to 2)
    }

    private val gson = Gson()

    suspend fun execute(movieId: String, catchupWindow: CatchupWindow? = null): List<StreamVariant> = withContext(Dispatchers.IO) {
        // Раньше — movieId.substringBefore('_') — работало, пока id был
        // буквально "m3u_N"/"xt_N". PlaylistSourceRepository.kt (мульти-
        // источники, "Источники") давно переписывает готовый id парсера в
        // "<UUID источника>_m3u_N" — чтобы не было коллизий между
        // несколькими M3U/Xtream-источниками одновременно. UUID сам состоит
        // из дефисов, не подчёркиваний, поэтому substringBefore('_') на
        // таком id возвращал ВЕСЬ UUID целиком — не совпадал ни с одним
        // известным префиксом, и код проваливался в третью ветку
        // (ScriptProvider-парсер), которая для собственного плейлиста
        // всегда возвращает пусто. Реальный репорт: "Нет потоков" на
        // каждом элементе собственного M3U/Xtream-плейлиста, каким бы
        // рабочим он ни был на самом деле — раз GetPlayableUrlUseCase в
        // принципе не мог дойти до playlistRepository.getStreamInfo().
        // Ищем маркер типа как подстроку, а не только как первый сегмент —
        // покрывает и старый голый формат (на случай ещё не мигрированных
        // записей), и новый с UUID источника впереди.
        val isPlaylist = PLAYLIST_PREFIXES.any { movieId.startsWith("${it}_") || movieId.contains("_${it}_") }
        val isBackend = ZENITH_BACKEND_PREFIXES.any { movieId.startsWith("${it}_") || movieId.contains("_${it}_") }
        val isChannel = movieId.startsWith(CHANNEL_PREFIX)
        when {
            isBackend -> executeWithPluginRace(movieId)
            // catchupWindow != null только когда вызывающий код (PlayerViewModel,
            // из player/{id}?catchupStart=...&catchupEnd=...) явно просит архив —
            // обычный переход "Смотреть" на канал catchupWindow не передаёт
            // вообще, ветка executeChannelFallback() не изменилась ни на строку.
            isChannel && catchupWindow != null -> executeChannelCatchup(movieId, catchupWindow)
            isChannel -> executeChannelFallback(movieId)
            isPlaylist -> {
                val info = playlistRepository.getStreamInfo(movieId)
                if (info != null) listOf(StreamVariant("Оригинал", info.url, source = "Мой плейлист", headers = info.headers)) else emptyList()
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

    /**
     * НЕ гонка с реальными сетевыми запросами здесь (см. заголовок класса,
     * пункт 3) — чисто сортировка уже известных данных. Порядок:
     * "alive" → "unknown" → "dead" (см. STATUS_RANK), внутри группы —
     * ChannelStreamEntity.priority по возрастанию. Живой эфир без вообще
     * ни одного ChannelStream (источник удалён/отписан) — пустой список,
     * тот же контракт, что и у playlist-ветки (info == null → emptyList()).
     */
    private suspend fun executeChannelFallback(channelId: String): List<StreamVariant> {
        // channelDao.getById() здесь не для данных о самом стриме (та
        // информация целиком в ChannelStreamEntity) — только чтобы у
        // "безымянного" стрима (rawTitle == null, ChannelMatchingRepository
        // не всегда получает осмысленное название от источника) был
        // human-readable фолбэк вместо голого "Источник N".
        val channel = channelDao.getById(channelId) ?: return emptyList()
        val streams = channelStreamDao.getByChannelId(channelId)
        if (streams.isEmpty()) return emptyList()
        return streams
            .sortedWith(compareBy({ CHANNEL_STATUS_RANK[it.lastCheckStatus] ?: 1 }, { it.priority }))
            .mapIndexed { index, stream -> stream.toStreamVariant(index, channel.canonicalName) }
    }

    private fun ChannelStreamEntity.toStreamVariant(index: Int, fallbackName: String): StreamVariant {
        val headers = buildMap {
            userAgent?.let { put("User-Agent", it) }
            referrer?.let { put("Referer", it) }
        }
        // quality — не разрешение, а человекочитаемая метка ИСТОЧНИКА
        // этого конкретного стрима (rawTitle — как называл этот канал
        // именно этот источник, см. ChannelStreamEntity) — тот же принцип
        // "видно, откуда вариант", что и StreamVariant.source для VOD.
        val label = rawTitle?.takeIf { it.isNotBlank() } ?: "$fallbackName (${index + 1})"
        return StreamVariant(quality = label, url = streamUrl, source = "Прямой эфир", headers = headers)
    }

    /**
     * PROMPT_EPG.md, подзадача 5 — тот же принцип, что и executeChannelFallback()
     * (вернуть ВСЕ пригодные варианты, не пытаться самим угадать один живой),
     * только источник URL другой (CatchupUrlBuilder, не streamUrl напрямую) и
     * фильтр по catchupDays > 0 — стрим без заявленной поддержки архива
     * вообще не участвует, ему нечего предложить на этот промежуток.
     */
    private suspend fun executeChannelCatchup(channelId: String, window: CatchupWindow): List<StreamVariant> {
        val channel = channelDao.getById(channelId) ?: return emptyList()
        val streams = channelStreamDao.getByChannelId(channelId).filter { it.catchupDays > 0 }
        if (streams.isEmpty()) return emptyList()
        return streams
            .sortedWith(compareBy({ CHANNEL_STATUS_RANK[it.lastCheckStatus] ?: 1 }, { it.priority }))
            .mapIndexedNotNull { index, stream ->
                val source = playlistSourceDao.getById(stream.sourceId) ?: return@mapIndexedNotNull null
                val url = CatchupUrlBuilder.build(stream, source, window.startMillis, window.endMillis) ?: return@mapIndexedNotNull null
                val headers = buildMap {
                    stream.userAgent?.let { put("User-Agent", it) }
                    stream.referrer?.let { put("Referer", it) }
                }
                val label = stream.rawTitle?.takeIf { it.isNotBlank() } ?: "${channel.canonicalName} (${index + 1})"
                StreamVariant(quality = label, url = url, source = "Архив эфира", headers = headers)
            }
    }
}
