package com.platinum.ott.data.repository

import android.content.Context
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import com.platinum.ott.data.playlist.M3uPlaylistParser
import com.platinum.ott.data.playlist.XmltvSaxParser
import com.platinum.ott.data.playlist.XtreamEpgClient
import com.platinum.ott.data.playlist.XtreamLiveStreamInfo
import com.platinum.ott.data.playlist.XtreamVodClient
import kotlinx.coroutines.Dispatchers
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

    suspend fun addM3uUrlSource(label: String, url: String, isLiveChannels: Boolean = false): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val entity = PlaylistSourceEntity(
            id = UUID.randomUUID().toString(), type = "m3u", label = label, url = url, priority = nextPriority(),
            contentKind = if (isLiveChannels) "live" else "vod"
        )
        sourceDao.upsert(entity)
        entity
    }

    /** См. заголовок класса — снапшот копируется один раз при добавлении. */
    suspend fun addM3uFileSource(label: String, fileContent: String, isLiveChannels: Boolean = false): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val snapshotFile = File(snapshotDir, "$id.m3u")
        snapshotFile.writeText(fileContent)
        val entity = PlaylistSourceEntity(
            id = id, type = "m3u", label = label, url = "file://${snapshotFile.absolutePath}", priority = nextPriority(),
            contentKind = if (isLiveChannels) "live" else "vod"
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
        sourceDao.deleteById(sourceId)
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        sourceDao.getEnabled().forEach { refresh(it.id, forceRefresh = false) }
    }

    suspend fun refresh(sourceId: String, forceRefresh: Boolean = true): Unit = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId) ?: return@withContext
        if (!forceRefresh) {
            val lastCache = movieDao.getLatestCacheTimeForSource(sourceId) ?: 0L
            if (System.currentTimeMillis() - lastCache <= SOURCE_REFRESH_TTL_MS) return@withContext
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
                    val rawEntries = M3uPlaylistParser.parse(body)
                    // PROMPT_EPG.md, подзадача 2 — парсится при каждом
                    // refresh() независимо от contentKind (обычный VOD-плейлист
                    // тоже технически может нести url-tvg, хотя типичный
                    // случай — именно contentKind == "live"). null тоже
                    // пишется явно: если провайдер уберёт url-tvg из шапки,
                    // источник не должен продолжать молча ссылаться на
                    // устаревший адрес.
                    val epgUrl = M3uPlaylistParser.parseEpgUrl(body)
                    sourceDao.updateEpgUrl(sourceId, epgUrl)
                    // PROMPT_EPG.md, подзадача 3 — независимо от того,
                    // "live" этот источник или "vod": обычный VOD-плейлист
                    // с url-tvg в шапке технически тоже может встретиться,
                    // хоть и нетипично (см. допущение у sourceDao.updateEpgUrl
                    // выше в предыдущей подзадаче).
                    if (epgUrl != null) refreshEpgFromXmltv(epgUrl)
                    if (source.contentKind == "live") {
                        // Живой эфир целиком уходит в Channel/ChannelStream —
                        // не пишется в playlist_movies вообще (см.
                        // PROMPT_IPTV_FOUNDATION.md, ChannelMatchingRepository).
                        channelMatchingRepository.matchAndStore(sourceId, rawEntries.map { it.toRawChannelCandidate() })
                    } else {
                        storeVodEntries(source, sourceId, rawEntries)
                    }
                }
                "xtream" -> {
                    val host = source.host; val user = source.username; val pass = source.password
                    if (host == null || user == null || pass == null) return@withContext
                    storeVodEntries(source, sourceId, XtreamVodClient.fetch(client, host, user, pass))

                    // Живой эфир — отдельный эндпоинт Xtream, не зависит от
                    // contentKind (то поле имеет смысл только для M3U — Xtream
                    // сам структурно разделяет VOD/live). fetchLiveStreams()
                    // сама ловит исключение "у панели вообще нет раздела live"
                    // и возвращает emptyList(), так что уже успешный VOD-фетч
                    // выше этим не роняется.
                    val liveStreams = XtreamVodClient.fetchLiveStreams(client, host, user, pass)
                    channelMatchingRepository.matchAndStore(sourceId, liveStreams.map { it.toRawChannelCandidate() })

                    // PROMPT_EPG.md, подзадача 3 — xmltv.php у Xtream не у
                    // каждой панели есть (это доп. эндпоинт сверх основного
                    // Xtream Codes API), refreshEpgFromXmltv() сама тихо
                    // проглатывает 404/сетевую ошибку, тем же принципом,
                    // что и fetchLiveStreams() выше для панелей без live.
                    refreshEpgFromXmltv(XtreamEpgClient.buildXmltvUrl(host, user, pass))
                }
                else -> {}
            }
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), "ok")
        } catch (e: Exception) {
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), e.message ?: "Ошибка обновления")
        }
    }

    private suspend fun storeVodEntries(source: PlaylistSourceEntity, sourceId: String, rawEntries: List<PlaylistMovieEntity>) {
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
        if (scoped.isNotEmpty()) {
            movieDao.deleteBySource(sourceId)
            movieDao.upsertAll(scoped)
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
    private suspend fun refreshEpgFromXmltv(url: String) {
        try {
            val now = System.currentTimeMillis()
            val windowStart = now - TimeUnit.HOURS.toMillis(2)
            val windowEnd = now + TimeUnit.HOURS.toMillis(48)
            val req = Request.Builder().url(url).build()
            val programs = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                resp.body?.byteStream()?.use { stream -> XmltvSaxParser.parse(stream, windowStart, windowEnd) } ?: emptyList()
            }
            if (programs.isEmpty()) return
            programs.groupBy { "ch_tvg_${it.channelId}" }.forEach { (channelId, channelPrograms) ->
                val entities = channelPrograms.map { p ->
                    EpgProgramEntity(
                        id = "$channelId:${p.startTimeMillis}",
                        channelId = channelId,
                        title = p.title,
                        description = p.description,
                        category = p.category,
                        startTimeMillis = p.startTimeMillis,
                        endTimeMillis = p.endTimeMillis
                    )
                }
                epgProgramRepository.replaceProgramsForChannel(channelId, entities)
            }
        } catch (_: Exception) {
            // См. комментарий в KDoc функции — источник без EPG/с битым
            // XMLTV не должен мешать основному контенту обновиться.
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
