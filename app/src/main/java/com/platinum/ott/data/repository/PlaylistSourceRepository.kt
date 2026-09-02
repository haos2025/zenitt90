package com.platinum.ott.data.repository

import android.content.Context
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.dao.PlaylistMovieDao
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import com.platinum.ott.data.playlist.M3uPlaylistParser
import com.platinum.ott.data.playlist.XtreamVodClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

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
    private val client: OkHttpClient
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

    suspend fun addM3uUrlSource(label: String, url: String): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val entity = PlaylistSourceEntity(id = UUID.randomUUID().toString(), type = "m3u", label = label, url = url, priority = nextPriority())
        sourceDao.upsert(entity)
        entity
    }

    /** См. заголовок класса — снапшот копируется один раз при добавлении. */
    suspend fun addM3uFileSource(label: String, fileContent: String): PlaylistSourceEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val snapshotFile = File(snapshotDir, "$id.m3u")
        snapshotFile.writeText(fileContent)
        val entity = PlaylistSourceEntity(id = id, type = "m3u", label = label, url = "file://${snapshotFile.absolutePath}", priority = nextPriority())
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
            val rawEntries: List<PlaylistMovieEntity> = when (source.type) {
                "m3u" -> {
                    val url = source.url ?: return@withContext
                    val body = if (url.startsWith("file://")) {
                        File(url.removePrefix("file://")).readText()
                    } else {
                        val req = Request.Builder().url(url).build()
                        client.newCall(req).execute().use { it.body?.string() ?: "" }
                    }
                    M3uPlaylistParser.parse(body)
                }
                "xtream" -> {
                    val host = source.host; val user = source.username; val pass = source.password
                    if (host == null || user == null || pass == null) return@withContext
                    XtreamVodClient.fetch(client, host, user, pass)
                }
                else -> emptyList()
            }
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
            // пришли — иначе временный сетевой сбой посреди refresh() стёр
            // бы уже рабочий кэш этого источника и заменил его пустотой
            // (тот же принцип, что был в предыдущей версии PlaylistRepository).
            if (scoped.isNotEmpty()) {
                movieDao.deleteBySource(sourceId)
                movieDao.upsertAll(scoped)
            }
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), "ok")
        } catch (e: Exception) {
            sourceDao.updateRefreshResult(sourceId, System.currentTimeMillis(), e.message ?: "Ошибка обновления")
        }
    }
}
