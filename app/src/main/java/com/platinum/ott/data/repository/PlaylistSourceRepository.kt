package com.platinum.ott.data.repository

import android.content.Context
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import com.platinum.ott.data.playlist.EntryClassifier
import com.platinum.ott.data.playlist.EntryKind
import com.platinum.ott.data.playlist.EpgFallbackSources
import com.platinum.ott.data.playlist.EpgNameMatcher
import com.platinum.ott.data.playlist.M3uPlaylistParser
import com.platinum.ott.data.playlist.ParsedEntry
import com.platinum.ott.data.playlist.XmltvSaxParser
import com.platinum.ott.data.playlist.XtreamEpgClient
import com.platinum.ott.data.playlist.XtreamLiveStreamInfo
import com.platinum.ott.data.playlist.XtreamVodClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

// Тот же час, что и у единственного источника раньше (PlaylistRepository,
// REFRESH_TTL_MS) — плейлисты бывают большие, не гонять на каждый вход.
private const val SOURCE_REFRESH_TTL_MS = 60 * 60 * 1000L

/**
 * Состояние загрузки телепрограммы для экрана «Настройки → Телепрограмма (EPG)».
 * [lines] — журнал текущего или последнего обновления; [ok] = null, пока ни разу не запускалось.
 */
data class EpgStatus(
    val running: Boolean = false,
    val lines: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val ok: Boolean? = null,
    val programs: Int = 0,
    val channelsWithEpg: Int = 0,
    val totalChannels: Int = 0
)

/** Отчёт, который при каждой новой строке публикует журнал целиком (для живого показа на экране). */
private class LiveReport(private val onChange: (List<String>) -> Unit) : ArrayList<String>() {
    override fun add(element: String): Boolean {
        val r = super.add(element)
        onChange(ArrayList(this))
        return r
    }
}

/** Итог разведения источника: сколько записей стало каналами, сколько — фильмами/сериалами. */
data class SourceSplit(val channels: Int, val vod: Int)

/** Группа (group-title) M3U-источника: счётчики по текущему разведению и текущее переопределение ("live"/"vod"/null). */
data class GroupStat(val name: String, val channels: Int, val vod: Int, val override: String?)

/**
 * До этой задачи (PROMPT_SOURCES_SCREEN.md) источник был один, его конфиг
 * целиком лежал в AuthPreferences (encrypted SharedPreferences), а фетч и
 * кэширование делал сам PlaylistRepository. Теперь это разделено:
 * PlaylistSourceRepository — CRUD источников (M3U/Xtream) + фетч контента
 * КОНКРЕТНОГО источника в playlist_movies; PlaylistRepository (соседний
 * файл) — только агрегация уже закэшированного контента всех включённых
 * источников для остального приложения.
 *
 * ВАЖНО про id (см. также комментарий у поля legacyIds в PlaylistSourceEntity):
 * M3uPlaylistParser/XtreamVodClient генерируют id БЕЗ привязки к источнику
 * ("m3u_0", "xt_123"...) — при нескольких источниках это коллизия. Для
 * НОВЫХ источников (добавленных через addM3uUrlSource/addM3uFileSource/
 * addXtreamSource) refresh() добавляет префикс sourceId и к id, и к
 * seriesId. Для мигрированного источника (legacyIds = true) префикс НЕ
 * добавляется — у текущих пользователей favorites/watch_history уже
 * ссылаются на старые id напрямую.
 */
class PlaylistSourceRepository(
    private val appContext: Context,
    private val authPreferences: AuthPreferences,
    private val sourceDao: PlaylistSourceDao,
    private val movieDao: PlaylistMovieDao,
    private val client: OkHttpClient,
    private val channelMatchingRepository: ChannelMatchingRepository,
    private val epgProgramRepository: EpgProgramRepository
) {
    // Локальные снапшоты M3U-файлов, добавленных через "Локальный файл"
    // (ACTION_OPEN_DOCUMENT). Решение сессии: копируем содержимое один раз
    // при импорте сюда, не держим persistable URI-permission на исходный
    // content:// и не перечитываем его при каждом refresh() — так снапшот
    // не зависит от того, жив ли ещё исходный файл/провайдер/флешка.
    private val snapshotDir: File by lazy {
        File(appContext.filesDir, "playlist_sources").apply { mkdirs() }
    }

    suspend fun getAll(): List<PlaylistSourceEntity> = withContext(Dispatchers.IO) { sourceDao.getAll() }

    // Итог последнего разведения каждого источника (для экрана результата после добавления).
    private val lastSplits = java.util.concurrent.ConcurrentHashMap<String, SourceSplit>()

    fun lastSplit(sourceId: String): SourceSplit? = lastSplits[sourceId]

    private fun m3uBodyFile(source: PlaylistSourceEntity): File {
        val url = source.url
        return if (url != null && url.startsWith("file://")) File(url.removePrefix("file://")) else bodySnapshotFile(source.id)
    }

    /**
     * Группы M3U-источника с числом записей-каналов и записей-фильмов по текущему разведению
     * (для экрана «Разделено неверно?»). Записи без group-title не показываются — переопределять нечего.
     */
    suspend fun getGroupStats(sourceId: String): List<GroupStat> = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext emptyList()
        if (source.type != "m3u") return@withContext emptyList()
        val file = m3uBodyFile(source)
        if (!file.exists()) return@withContext emptyList()
        val parsed = M3uPlaylistParser.parseEntries(file.readText())
        val overrides = EntryClassifier.parseOverrides(source.groupOverrides)
        val kinds = EntryClassifier.classifyAll(parsed, source.contentKind, overrides)
        val counts = LinkedHashMap<String, IntArray>()
        parsed.forEachIndexed { i, e ->
            val g = e.rawGroup ?: return@forEachIndexed
            val a = counts.getOrPut(g) { IntArray(2) }
            if (kinds[i] == EntryKind.CHANNEL) a[0]++ else a[1]++
        }
        counts.map { (name, a) ->
            GroupStat(name, a[0], a[1], overrides[name] ?: overrides.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getContentCount(sourceId: String): Int = withContext(Dispatchers.IO) {
        movieDao.getCountBySource(sourceId)
    }

    /**
     * Разовая миграция: если PlaylistSource ещё ни одного нет, а
     * AuthPreferences уже настроен (старая схема, единственный источник) —
     * переносим его как первую запись, не теряя то, что уже подключено у
     * текущих пользователей. AuthPreferences НЕ очищается — syncToken/
     * lastSyncTimestamp там остаются нужны сами по себе (device id для
     * /sync, см. AuthPreferences.getOrCreateSyncToken()); type/host/username/
     * password/m3uUrl становятся историческими и дальше нигде не читаются,
     * но оставлены как есть — реального вреда от неиспользуемых полей нет,
     * а очистка потребовала бы отдельно доказывать, что миграция везде
     * прошла успешно.
     *
     * Идемпотентна (ранний выход по sourceDao.getCount() > 0), поэтому
     * безопасно вызывать при каждом initAuth()/reinitWithAuth().
     */
    suspend fun migrateLegacySourceIfNeeded() = withContext(Dispatchers.IO) {
        if (sourceDao.getCount() > 0) return@withContext
        val type = authPreferences.type ?: return@withContext
        val entity = when (type) {
            "m3u" -> {
                val url = authPreferences.m3uUrl ?: return@withContext
                PlaylistSourceEntity(id = UUID.randomUUID().toString(), type = "m3u", label = "Мой плейлист", url = url, priority = 0, legacyIds = true)
            }
            "xtream" -> {
                val host = authPreferences.host; val user = authPreferences.username; val pass = authPreferences.password
                if (host == null || user == null || pass == null) return@withContext
                PlaylistSourceEntity(id = UUID.randomUUID().toString(), type = "xtream", label = "Мой плейлист", host = host, username = user, password = pass, priority = 0, legacyIds = true)
            }
            else -> return@withContext
        }
        sourceDao.upsert(entity)
        // Уже закэшированные строки playlist_movies (от единственного
        // старого источника) физически принадлежат этому мигрированному
        // источнику — проставляем sourceId задним числом, ничего не
        // перекачивая заново, чтобы карточка источника в UI сразу
        // показывала реальное количество контента, а не 0 до первого
        // refresh() по TTL.
        movieDao.assignSourceIdWhereNull(entity.id)
    }

    /**
     * Та же проверка, что раньше делал AuthRepositoryImpl.validateAndSaveM3U()
     * для единственного источника — HTTP-успех + "#EXTINF" в теле (иначе это
     * не M3U). Здесь только проверка, ничего не сохраняет — вызывается ДО
     * addM3uUrlSource() из UI экрана добавления, чтобы плохой адрес не
     * попадал в список источников молча (обнаружился бы только на первом
     * фоновом refresh()).
     */
    suspend fun validateM3uUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body?.string() ?: ""
                if (!body.contains("#EXTINF")) return@withContext Result.failure(Exception("Не M3U-плейлист"))
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Та же проверка, что раньше делал AuthRepositoryImpl.validateAndSaveXtream(). */
    suspend fun validateXtream(host: String, username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${host.trimEnd('/')}/player_api.php?username=$username&password=$password"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addM3uUrlSource(label: String, url: String, isLiveChannels: Boolean = false, mode: String? = null): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val entity = PlaylistSourceEntity(
            id = UUID.randomUUID().toString(), type = "m3u", label = label, url = url, priority = nextPriority(),
            contentKind = mode ?: if (isLiveChannels) EntryClassifier.MODE_LIVE else EntryClassifier.MODE_AUTO
        )
        sourceDao.upsert(entity)
        entity
    }

    /** См. заголовок класса — снапшот копируется один раз при добавлении. */
    suspend fun addM3uFileSource(label: String, fileContent: String, isLiveChannels: Boolean = false, mode: String? = null): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val snapshotFile = File(snapshotDir, "$id.m3u")
        snapshotFile.writeText(fileContent)
        val entity = PlaylistSourceEntity(
            id = id, type = "m3u", label = label, url = "file://${snapshotFile.absolutePath}", priority = nextPriority(),
            contentKind = mode ?: if (isLiveChannels) EntryClassifier.MODE_LIVE else EntryClassifier.MODE_AUTO
        )
        sourceDao.upsert(entity)
        entity
    }

    /** Повторный импорт того же файлового источника — перезаписывает снапшот и обновляет каталог. */
    suspend fun reimportFile(sourceId: String, fileContent: String) = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        val path = source.url?.removePrefix("file://") ?: return@withContext
        File(path).writeText(fileContent)
        refresh(sourceId)
    }

    suspend fun addXtreamSource(label: String, host: String, username: String, password: String): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val entity = PlaylistSourceEntity(id = UUID.randomUUID().toString(), type = "xtream", label = label, host = host, username = username, password = password, priority = nextPriority())
        sourceDao.upsert(entity)
        entity
    }

    suspend fun updateLabel(sourceId: String, label: String) = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        sourceDao.upsert(source.copy(label = label))
    }

    suspend fun setEnabled(sourceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setEnabled(sourceId, enabled)
    }

    // Приоритет — кнопками вверх/вниз, не drag-and-drop (см. обоснование в
    // PROMPT_SOURCES_SCREEN.md — на TV пультом перетаскивать ненадёжно),
    // поэтому реализация — перестановка значения priority с соседом по
    // списку, а не пересчёт всех строк сразу.
    suspend fun moveUp(sourceId: String) = withContext(Dispatchers.IO) { swapWithNeighbor(sourceId, up = true) }
    suspend fun moveDown(sourceId: String) = withContext(Dispatchers.IO) { swapWithNeighbor(sourceId, up = false) }

    private suspend fun swapWithNeighbor(sourceId: String, up: Boolean) {
        val ordered = sourceDao.getAll()
        val index = ordered.indexOfFirst { it.id == sourceId }
        if (index == -1) return
        val neighborIndex = if (up) index - 1 else index + 1
        if (neighborIndex < 0 || neighborIndex >= ordered.size) return
        val current = ordered[index]; val neighbor = ordered[neighborIndex]
        sourceDao.setPriority(current.id, neighbor.priority)
        sourceDao.setPriority(neighbor.id, current.priority)
    }

    private suspend fun nextPriority(): Int = (sourceDao.getMaxPriority() ?: -1) + 1

    suspend fun delete(sourceId: String) = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        movieDao.deleteBySource(sourceId)
        source.url?.let { url -> if (url.startsWith("file://")) runCatching { File(url.removePrefix("file://")).delete() } }
        runCatching { bodySnapshotFile(sourceId).delete() }
        // Каналы источника уходят вместе с ним; неподписанные сироты чистятся, подписанные остаются.
        runCatching { channelMatchingRepository.clearSourceAndCleanOrphans(sourceId) }
        sourceDao.deleteById(sourceId)
    }

    // ФИКС (аудит): раньше — sourceDao.getEnabled().forEach { refresh(...) },
    // строго по очереди. Источники независимы (каждый пишет только в свои
    // строки по sourceId), поэтому при нескольких источниках это была
    // самостоятельная причина "IPTV долго обновляется" — M3U/Xtream VOD/
    // live/EPG одного источника ждали своей очереди, пока полностью не
    // закончит предыдущий. Параллелим через async/awaitAll.
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        coroutineScope {
            sourceDao.getEnabled().map { source -> async { refresh(source.id, forceRefresh = false) } }.awaitAll()
        }
    }

    // Фоновая работа репозитория: EPG и «тихое» обновление источников, у которых уже есть кэш.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Один XMLTV за раз — два параллельных разбора больших файлов съедают память ТВ-приставки.
    private val epgMutex = Mutex()
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun launchEpg(block: suspend () -> Unit) {
        backgroundScope.launch {
            epgMutex.withLock {
                try { block() } catch (_: Exception) { /* EPG — дополнение, не ломает каталог */ }
            }
        }
    }

    /** Есть ли у источника уже сохранённые данные (тогда главную можно не задерживать обновлением). */
    suspend fun hasCachedData(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext false
        (movieDao.getLatestCacheTimeForSource(sourceId) ?: 0L) > 0L ||
            (source.lastRefreshStatus == "ok" && (source.lastRefreshedAt ?: 0L) > 0L)
    }

    /** Обновить источник в фоне (по TTL), не дожидаясь; повторный запуск того же источника игнорируется. */
    fun refreshInBackground(sourceId: String) {
        if (!inFlight.add(sourceId)) return
        backgroundScope.launch {
            try { refresh(sourceId, forceRefresh = false) } finally { inFlight.remove(sourceId) }
        }
    }

    suspend fun refresh(sourceId: String, forceRefresh: Boolean = true): Unit = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        if (!forceRefresh) {
            // Свежесть: по строкам каталога ИЛИ по времени последнего УСПЕШНОГО обновления.
            // У источника живых каналов строк в playlist_movies нет вовсе — раньше он считался
            // «протухшим» всегда и перекачивался при каждом открытии главной.
            val lastCache = movieDao.getLatestCacheTimeForSource(sourceId) ?: 0L
            val lastOk = if (source.lastRefreshStatus == "ok") (source.lastRefreshedAt ?: 0L) else 0L
            if (System.currentTimeMillis() - maxOf(lastCache, lastOk) <= SOURCE_REFRESH_TTL_MS) return@withContext
        }
        try {
            when (source.type) {
                "m3u" -> {
                    val url = source.url ?: return@withContext
                    val body = if (url.startsWith("file://")) {
                        File(url.removePrefix("file://")).readText()
                    } else {
                        val req = Request.Builder().url(url).build()
                        client.newCall(req).execute().use { it.body?.string() ?: "" }
                    }
                    // Тело плейлиста по ссылке сохраняем рядом со снапшотами файловых источников —
                    // чтобы пересчитать разведение (переопределение группы) без повторной загрузки.
                    if (!url.startsWith("file://") && body.isNotBlank()) {
                        runCatching { bodySnapshotFile(sourceId).writeText(body) }
                    }
                    processM3uBody(source, sourceId, body)
                }
                "xtream" -> {
                    val host = source.host; val user = source.username; val pass = source.password
                    if (host == null || user == null || pass == null) return@withContext
                    val vodItems = XtreamVodClient.fetch(client, host, user, pass)
                    storeVodEntries(source, sourceId, vodItems)

                    // Живой эфир — отдельный эндпоинт Xtream, не зависит от
                    // contentKind (то поле имеет смысл только для M3U — Xtream
                    // сам структурно разделяет VOD/live). fetchLiveStreams()
                    // сама ловит исключение "у панели вообще нет раздела live"
                    // и возвращает emptyList(), так что уже успешный VOD-фетч
                    // выше этим не роняется.
                    val liveStreams = XtreamVodClient.fetchLiveStreams(client, host, user, pass)
                    val storedStreams = channelMatchingRepository.matchAndStore(sourceId, liveStreams.map { it.toRawChannelCandidate() })
                    lastSplits[sourceId] = SourceSplit(liveStreams.size, vodItems.size)

                    // ФИКС (аудит): xmltv.php есть не у каждой Xtream-панели —
                    // если он не дал ни одной программы, используем per-channel
                    // fallback (get_short_epg), который раньше был реализован,
                    // но никогда не вызывался.
                    launchEpg {
                        val xmltvOk = refreshEpgFromXmltv(XtreamEpgClient.buildXmltvUrl(host, user, pass))
                        if (!xmltvOk) {
                            refreshEpgFromShortEpgFallback(host, user, pass, storedStreams)
                            refreshEpgFromFallbackSources()
                        }
                    }
                }
                else -> {}
            }
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), "ok")
        } catch (e: Exception) {
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), e.message ?: "Ошибка обновления")
        }
    }

    /** «Управление кэшем»: удаляет сохранённые тела плейлистов по ссылке (`<id>.m3u`); файловые снапшоты НЕ трогает. */
    suspend fun clearBodyCache() = withContext(Dispatchers.IO) {
        sourceDao.getAll().filter { it.type == "m3u" && it.url?.startsWith("file://") != true }
            .forEach { runCatching { bodySnapshotFile(it.id).delete() } }
    }

    private fun bodySnapshotFile(sourceId: String) = File(snapshotDir, "$sourceId.m3u")

    /**
     * Разбирает тело M3U, разводит записи на каналы и фильмы/сериалы (EntryClassifier) и
     * записывает обе стороны. Если разбор дал записи, обе стороны ЗАМЕНЯЮТСЯ — в том числе
     * пустой (иначе после смены классификации старые фильмы/каналы остались бы).
     * Пустой разбор (сетевой сбой, битый файл) ничего не стирает.
     */
    private suspend fun processM3uBody(source: PlaylistSourceEntity, sourceId: String, body: String) {
        val parsed = M3uPlaylistParser.parseEntries(body)
        // PROMPT_EPG.md, подзадача 2: url-tvg из шапки; null пишется явно (см. историю в git).
        val epgUrl = M3uPlaylistParser.parseEpgUrl(body)
        sourceDao.updateEpgUrl(sourceId, epgUrl)
        val ownEpgUrls = M3uPlaylistParser.parseEpgUrls(epgUrl)

        val kinds = EntryClassifier.classifyAll(parsed, source.contentKind, EntryClassifier.parseOverrides(source.groupOverrides))
        val channelEntries = ArrayList<ParsedEntry>()
        val vodEntries = ArrayList<ParsedEntry>()
        parsed.forEachIndexed { i, e -> if (kinds[i] == EntryKind.CHANNEL) channelEntries.add(e) else vodEntries.add(e) }

        if (parsed.isNotEmpty()) lastSplits[sourceId] = SourceSplit(channelEntries.size, vodEntries.size)

        if (parsed.isNotEmpty()) {
            if (channelEntries.isNotEmpty()) {
                // Живой эфир уходит в Channel/ChannelStream, не в playlist_movies.
                channelMatchingRepository.matchAndStore(sourceId, channelEntries.map { it.entity.toRawChannelCandidate() })
                channelMatchingRepository.cleanOrphans()
            } else {
                channelMatchingRepository.clearSourceAndCleanOrphans(sourceId)
            }
            storeVodEntries(source, sourceId, vodEntries.map { it.entity }, replaceEvenIfEmpty = true)
        }

        if (channelEntries.isNotEmpty()) {
            // EPG — ПОСЛЕ сохранения каналов (сопоставление по названию идёт по уже созданным каналам),
            // фоном: каталог не ждёт скачивания/разбора XMLTV.
            launchEpg {
                val handled = mutableSetOf<String>()
                var epgOk = false
                ownEpgUrls.forEach { if (refreshEpgFromXmltv(it, null, handled)) epgOk = true }
                if (!epgOk) refreshEpgFromFallbackSources(null, handled)
            }
        } else {
            launchEpg { ownEpgUrls.forEach { refreshEpgFromXmltv(it) } }
        }
    }

    /**
     * Пересчитать разведение M3U-источника после смены переопределений групп/режима.
     * Берёт сохранённое тело (файловый снапшот или `<id>.m3u`) — без загрузки из сети;
     * если тела нет, делает обычное обновление.
     */
    suspend fun reclassify(sourceId: String) = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        if (source.type != "m3u") return@withContext
        val file = m3uBodyFile(source)
        if (!file.exists()) { refresh(sourceId, forceRefresh = true); return@withContext }
        try {
            processM3uBody(source, sourceId, file.readText())
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), "ok")
        } catch (e: Exception) {
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), e.message ?: "Ошибка пересчёта")
        }
    }

    /** Сохранить переопределения групп ("group-title" → "live"/"vod") и пересчитать; пустая карта = сброс. */
    suspend fun setGroupOverrides(sourceId: String, overrides: Map<String, String>) = withContext(Dispatchers.IO) {
        sourceDao.updateGroupOverrides(sourceId, EntryClassifier.overridesToJson(overrides))
        reclassify(sourceId)
    }

    /** Режим разведения M3U: "auto" | "live" | "vod" — и пересчёт. */
    suspend fun setContentKind(sourceId: String, kind: String) = withContext(Dispatchers.IO) {
        sourceDao.updateContentKind(sourceId, kind)
        reclassify(sourceId)
    }

    private suspend fun storeVodEntries(source: PlaylistSourceEntity, sourceId: String, rawEntries: List<PlaylistMovieEntity>, replaceEvenIfEmpty: Boolean = false) {
        val scoped = rawEntries.map { entry ->
            if (source.legacyIds) {
                entry.copy(sourceId = sourceId)
            } else {
                entry.copy(
                    id = "${sourceId}_${entry.id}",
                    sourceId = sourceId,
                    seriesId = entry.seriesId?.let { "${sourceId}_$it" }
                )
            }
        }
        // Не чистим таблицу, пока не убедились что новые данные реально
        // пришли — иначе временный сетевой сбой посреди refresh() стёр бы
        // уже рабочий кэш этого источника и заменил его пустотой (тот же
        // принцип, что был в предыдущей версии PlaylistRepository).
        if (scoped.isNotEmpty() || replaceEvenIfEmpty) {
            movieDao.deleteBySource(sourceId)
            if (scoped.isNotEmpty()) movieDao.upsertAll(scoped)
        }
    }

    private fun PlaylistMovieEntity.toRawChannelCandidate() = RawChannelCandidate(
        tvgId = tvgId, name = title, logo = poster, category = genre,
        streamUrl = streamUrl, userAgent = userAgent, referrer = referrer,
        catchupDays = catchupDays ?: 0, catchupTemplate = catchupTemplate,
        channelNumber = channelNumber
    )

    private fun XtreamLiveStreamInfo.toRawChannelCandidate() = RawChannelCandidate(
        tvgId = tvgId, name = name, logo = logo, category = categoryName, streamUrl = streamUrl,
        externalStreamId = streamId.toString(), catchupDays = catchupDays, channelNumber = channelNumber
    )

    /**
     * PROMPT_EPG.md, подзадача 3 — общая точка для обоих источников XMLTV
     * (M3U url-tvg и Xtream xmltv.php): скачивает и потоково (SAX,
     * XmltvSaxParser) разбирает файл, оставляя только слоты внутри
     * скользящего окна (−2ч/+48ч, то же окно, что и в
     * EpgProgramRepository.cleanupOutsideWindow() — прошлый край должен
     * совпадать, иначе только что записанные, но уже "устаревшие" по
     * меркам этой функции программы тут же подчистит EpgCleanupWorker),
     * группирует по каналу (channel="X" → тот же "ch_tvg_X", что и
     * ChannelMatchingRepository.resolveChannelId() для tvg-id) и заменяет
     * расписание каждого затронутого канала.
     *
     * Отдельный try/catch, НЕ пробрасывается наверх в refresh(): EPG —
     * дополнение к основному контенту источника, а не равноправная часть
     * его успеха/провала — недоступный/битый XMLTV не должен помечать
     * успешно обновившийся VOD/live-каталог как "Ошибка обновления" в
     * карточке источника (тот же принцип, что и в fetchLiveStreams()/
     * fetchSeriesEpisodes() в XtreamVodClient — частичный сбой изолирован).
     *
     * ДОПУЩЕНИЕ (честно, не проверено на реальном файле в десятки МБ на
     * слабом устройстве): SAX-обработчик не копит вне окна, но сам поток
     * от OkHttp читается за один проход целиком — если реальный XMLTV
     * окажется на порядок больше "десятков МБ" из PROMPT_EPG.md, стоит
     * перепроверить отдельно, здесь это не тестировалось.
     */
    private suspend fun refreshEpgFromXmltv(
        url: String,
        report: MutableList<String>? = null,
        handled: MutableSet<String> = mutableSetOf()
    ): Boolean {
        val shortUrl = url.substringAfter("://").substringBefore('?').take(60)
        report?.add("Загружаю: $shortUrl …")
        return try {
            val now = System.currentTimeMillis()
            val windowStart = now - TimeUnit.HOURS.toMillis(2)
            val windowEnd = now + TimeUnit.HOURS.toMillis(48)
            // Отдельный клиент: XMLTV — файл на десятки МБ, общий API-таймаут (15/30 с) его обрывал.
            // User-Agent «как у браузера» — часть серверов EPG отдаёт 403 библиотечным клиентам.
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .build()
            val xmltvNames = mutableMapOf<String, MutableList<String>>()
            // Потоковый фильтр: в память попадают только программы каналов НАШИХ плейлистов
            // (по tvg-id или по названию из <channel>, которые в XMLTV идут раньше <programme>).
            // Раньше копились все программы всех каналов файла на 48 часов — на ТВ-приставке это
            // сотни тысяч объектов и OutOfMemoryError.
            val ourTvg = HashSet<String>()
            val ourNameKeys = HashSet<String>()
            for (c in channelMatchingRepository.allChannels()) {
                c.tvgId?.let { ourTvg += it.lowercase().trim() }
                ourNameKeys += EpgNameMatcher.keys(c.canonicalName)
            }
            val accept = { xid: String ->
                xid.lowercase().trim() in ourTvg ||
                    (xmltvNames[xid].orEmpty() + xid.substringBeforeLast('.')).any { n ->
                        EpgNameMatcher.keys(n).any { it in ourNameKeys }
                    }
            }
            var httpCode = 0
            val programs = epgClient.newCall(req).execute().use { resp ->
                httpCode = resp.code
                if (!resp.isSuccessful) return@use emptyList()
                resp.body?.byteStream()?.use { raw ->
                    // XMLTV часто отдают как .xml.gz БЕЗ заголовка Content-Encoding — OkHttp
                    // такой файл сам не распакует, поэтому смотрим на магические байты gzip.
                    val buffered = java.io.BufferedInputStream(raw, 8192)
                    buffered.mark(2)
                    val b0 = buffered.read(); val b1 = buffered.read()
                    buffered.reset()
                    val stream: java.io.InputStream =
                        if (b0 == 0x1f && b1 == 0x8b) java.util.zip.GZIPInputStream(buffered, 8192) else buffered
                    XmltvSaxParser.parse(stream, windowStart, windowEnd, accept = accept) { id, name ->
                        xmltvNames.getOrPut(id) { mutableListOf() }.add(name)
                    }
                } ?: emptyList()
            }
            if (programs.isEmpty()) {
                report?.add("$shortUrl: " + if (httpCode in 200..299) "файл получен, программ в окне −2ч…+48ч нет" else "HTTP $httpCode")
                return false
            }
            val byXmltv = programs.groupBy { it.channelId }
            val byLower = HashMap<String, String>()
            for (xid in byXmltv.keys) byLower.putIfAbsent(xid.lowercase().trim(), xid)
            val nameIndex = HashMap<String, String>()
            for (xid in byXmltv.keys) {
                for (n in (xmltvNames[xid].orEmpty() + xid.substringBeforeLast('.'))) {
                    for (k in EpgNameMatcher.keys(n)) nameIndex.putIfAbsent(k, xid)
                }
            }
            var byId = 0; var byName = 0
            for (ch in channelMatchingRepository.allChannels()) {
                // 1) по tvg-id без учёта регистра; 2) по названию (только если по id не нашлось).
                var xid = ch.tvgId?.let { byLower[it.lowercase().trim()] }
                var viaName = false
                if (xid == null) {
                    if (ch.id in handled) continue
                    xid = EpgNameMatcher.keys(ch.canonicalName).firstNotNullOfOrNull { nameIndex[it] }
                    viaName = xid != null
                }
                val list = byXmltv[xid ?: continue] ?: continue
                epgProgramRepository.replaceProgramsForChannel(
                    ch.id,
                    list.map { p ->
                        EpgProgramEntity(
                            id = "${ch.id}:${p.startTimeMillis}", channelId = ch.id, title = p.title,
                            description = p.description, category = p.category,
                            startTimeMillis = p.startTimeMillis, endTimeMillis = p.endTimeMillis
                        )
                    }
                )
                handled += ch.id
                if (viaName) byName++ else byId++
            }
            report?.add("$shortUrl: программ ${programs.size}, каналов в файле ${byXmltv.size}; сопоставлено по id $byId, по названию $byName")
            true
        } catch (e: OutOfMemoryError) {
            // Error, не Exception: раньше такой сбой ронял приложение или молча обрывал загрузку.
            report?.add("$shortUrl: не хватило памяти при разборе файла")
            false
        } catch (e: Exception) {
            // Источник без EPG/с битым XMLTV не должен мешать основному контенту обновиться.
            report?.add("$shortUrl: ошибка ${e.javaClass.simpleName}: ${e.message?.take(80)}")
            false
        }
    }

    /** Запасные источники: по очереди, пока один из них не даст программу. */
    private suspend fun refreshEpgFromFallbackSources(report: MutableList<String>? = null, handled: MutableSet<String> = mutableSetOf()) {
        for (url in EpgFallbackSources.URLS) {
            if (refreshEpgFromXmltv(url, report, handled)) return
        }
    }

    private val epgPrefs by lazy { appContext.getSharedPreferences("epg_state", Context.MODE_PRIVATE) }

    // ---------- Ручное/фоновое обновление программы с видимым ходом (экран «Телепрограмма (EPG)») ----------

    private val _epgStatus by lazy {
        val saved = epgPrefs.getString("last_report", null)
        MutableStateFlow(
            EpgStatus(
                lines = saved?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
                finishedAt = epgPrefs.getLong("last_report_at", 0L),
                ok = if (epgPrefs.contains("last_report_ok")) epgPrefs.getBoolean("last_report_ok", false) else null,
                programs = epgPrefs.getInt("last_programs", 0),
                channelsWithEpg = epgPrefs.getInt("last_channels_with_epg", 0),
                totalChannels = epgPrefs.getInt("last_total_channels", 0)
            )
        )
    }
    val epgStatus: StateFlow<EpgStatus> get() = _epgStatus.asStateFlow()
    private val epgRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Запустить обновление программы в фоне (не блокирует; повторный запуск во время работы игнорируется). */
    fun startEpgRefresh() {
        if (!epgRunning.compareAndSet(false, true)) return
        backgroundScope.launch {
            try { runTrackedEpgRefresh() } finally { epgRunning.set(false) }
        }
    }

    private suspend fun runTrackedEpgRefresh() {
        val started = System.currentTimeMillis()
        _epgStatus.value = EpgStatus(running = true, lines = listOf("Обновление программы начато…"), startedAt = started)
        val report = LiveReport { lines -> _epgStatus.value = _epgStatus.value.copy(lines = lines) }
        val ok = try {
            refreshEpgOnly(report)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            report.add("Ошибка: ${e.message}")
            false
        }
        val (programs, channelsWithEpg) = runCatching { epgProgramRepository.stats() }.getOrDefault(0 to 0)
        val total = runCatching { channelMatchingRepository.allChannels().size }.getOrDefault(0)
        val finished = System.currentTimeMillis()
        report.add(
            (if (ok) "Готово" else "Программу получить не удалось") +
                ": программ в базе $programs, каналов с программой $channelsWithEpg из $total, " +
                "заняло ${(finished - started) / 1000} с"
        )
        epgPrefs.edit()
            .putString("last_report", report.joinToString("\n"))
            .putLong("last_report_at", finished).putBoolean("last_report_ok", ok)
            .putInt("last_programs", programs).putInt("last_channels_with_epg", channelsWithEpg).putInt("last_total_channels", total)
            .apply()
        _epgStatus.value = EpgStatus(
            running = false, lines = ArrayList(report), startedAt = started, finishedAt = finished,
            ok = ok, programs = programs, channelsWithEpg = channelsWithEpg, totalChannels = total
        )
    }
    private val epgClient by lazy {
        client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    /**
     * Обновляет ТОЛЬКО программу передач (без перезагрузки каталогов): url-tvg плейлистов,
     * xmltv.php Xtream; если ни один источник не дал программу — запасные (EpgFallbackSources).
     * report — построчный отчёт для показа пользователю (что скачано и сколько каналов сопоставлено).
     */
    suspend fun refreshEpgOnly(report: MutableList<String>? = null) = withContext(Dispatchers.IO) {
        var anyOk = false
        val handled = mutableSetOf<String>()
        val sources = sourceDao.getEnabled()
        if (sources.isEmpty()) report?.add("Нет включённых источников")
        for (source in sources) {
            when (source.type) {
                "m3u" -> {
                    val urls = M3uPlaylistParser.parseEpgUrls(source.epgUrl)
                    if (urls.isEmpty()) report?.add("«${source.label}»: в плейлисте нет адреса программы (url-tvg)")
                    urls.forEach { if (refreshEpgFromXmltv(it, report, handled)) anyOk = true }
                }
                "xtream" -> {
                    val host = source.host; val user = source.username; val pass = source.password
                    if (host != null && user != null && pass != null &&
                        refreshEpgFromXmltv(XtreamEpgClient.buildXmltvUrl(host, user, pass), report, handled)) anyOk = true
                }
            }
        }
        if (!anyOk) {
            report?.add("Свои источники программы не дали — пробую запасные")
            val before = handled.size
            refreshEpgFromFallbackSources(report, handled)
            if (handled.size > before) anyOk = true
        }
        if (anyOk) epgPrefs.edit().putLong("last_ok", System.currentTimeMillis()).apply()
        anyOk
    }

    /** Фоновый запуск при открытии каналов: не чаще раза в maxAgeMs. */
    suspend fun refreshEpgIfStale(maxAgeMs: Long) {
        if (System.currentTimeMillis() - epgPrefs.getLong("last_ok", 0L) < maxAgeMs) return
        startEpgRefresh()
    }

    /**
     * ФИКС (аудит): раньше это был единственный способ получить EPG для
     * Xtream, и его молчаливый провал (панель без xmltv.php — это НЕ
     * редкость, отдельный опциональный эндпоинт сверх основной Xtream
     * Codes API) означал, что канал остаётся без расписания насовсем,
     * хотя get_short_epg/get_epg (XtreamEpgClient) для него уже был
     * реализован — просто нигде не вызывался. Здесь — по каждому каналу
     * с известным externalStreamId, чанками по 4 параллельно (тот же
     * принцип, что в ChannelHealthChecker, не бьём панель одним махом
     * и не ждём все каналы строго по очереди).
     */
    private suspend fun refreshEpgFromShortEpgFallback(
        host: String, user: String, pass: String, streams: List<com.platinum.ott.data.local.entity.ChannelStreamEntity>
    ) {
        streams.filter { it.externalStreamId != null }
            .chunked(4)
            .forEach { chunk ->
                coroutineScope {
                    chunk.map { stream ->
                        async(Dispatchers.IO) {
                            try {
                                val programs = XtreamEpgClient.getShortEpg(client, host, user, pass, stream.externalStreamId!!)
                                epgProgramRepository.upsertFromXtream(stream.channelId, programs)
                            } catch (_: Exception) {
                                // Один недоступный канал не должен останавливать fallback для остальных.
                            }
                        }
                    }.awaitAll()
                }
            }
    }

    /**
     * PROMPT_EPG.md, подзадача 2 — единая точка входа для будущих
     * подзадач (5: сетка программ), чтобы им не приходилось знать разницу
     * между "URL уже лежит в поле" (M3U) и "URL строится на месте"
     * (Xtream) — тот же принцип инкапсуляции, что и у streamUrl в
     * XtreamVodClient (вызывающий код не собирает ссылки на потоки вручную).
     */
    fun epgSourceUrl(source: PlaylistSourceEntity): String? = when (source.type) {
        "m3u" -> source.epgUrl
        "xtream" -> {
            val host = source.host; val user = source.username; val pass = source.password
            if (host != null && user != null && pass != null) XtreamEpgClient.buildXmltvUrl(host, user, pass) else null
        }
        else -> null
    }
}
