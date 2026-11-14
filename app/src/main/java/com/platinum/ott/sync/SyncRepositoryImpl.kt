package com.platinum.ott.sync

import android.content.Context
import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.dao.SyncTombstoneDao
import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.security.MessageDigest

/**
 * Синхронизация через сервер, протокол v2 (zenith-backend /v2/*, см. sync_v2.py):
 *  - устройство регистрируется один раз и получает токен (Bearer) вместо «голого» device_id;
 *  - изменения приходят по СЕРВЕРНОМУ курсору (v2Cursor), а не по клиентскому времени —
 *    запись, сделанная офлайн «в прошлом», всё равно доходит до остальных устройств;
 *  - удаления передаются метками (SyncTombstoneEntity), конфликты решает updatedAt (новее — выигрывает);
 *  - после каждого сеанса — отчёт (SyncReport) и подтверждение получения (ack с контрольной суммой).
 *
 * Локальные поля избранного (папка, ручной порядок, флаг аниме) по-прежнему остаются локальными.
 */
class SyncRepositoryImpl(
    private val appContext: Context,
    private val api: ZenithApiService,
    private val favDao: FavoritesDao,
    private val histDao: WatchHistoryDao,
    private val tombDao: SyncTombstoneDao,
    private val prefs: AuthPreferences,
    private val reports: SyncReportStore
) : SyncRepository {

    // ---------------------------------------------------------------- синхронизация

    override suspend fun sync(trigger: String): Result<SyncReport> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val startedAt = System.currentTimeMillis()
            try {
                val report = try {
                    doSync(trigger, startedAt)
                } catch (e: HttpException) {
                    if (e.code() != 401) throw e
                    // Сервер не знает наш токен: данные на сервере сброшены или устройство отвязано
                    // с другого устройства. Регистрируемся заново (в новую, пустую группу) и повторяем.
                    markCredentialsLost()
                    doSync(trigger, startedAt)
                }
                reports.add(report)
                prefs.lastSyncTimestamp = report.finishedAt
                Result.success(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = friendlyError(e)
                // Фоновые попытки без сети не засоряют историю отчётов.
                if (trigger != "auto" || e !is IOException) {
                    reports.add(SyncReport(startedAt = startedAt, finishedAt = System.currentTimeMillis(), trigger = trigger, ok = false, error = message))
                }
                Result.failure(Exception(message, e))
            }
        }
    }

    private suspend fun doSync(trigger: String, startedAt: Long): SyncReport {
        ensureRegistered()
        val auth = bearer()
        var cursor = prefs.v2Cursor
        var pushSince = prefs.lastPushAt
        val pushStartedAt = System.currentTimeMillis()
        val notes = mutableListOf<String>()
        val fav = Counters()
        val hist = Counters()

        // 1. Получаем изменения других устройств (постранично).
        var pages = 0
        while (true) {
            val page = api.pullSyncV2(auth, cursor)
            if (page.resetRequired) {
                pushSince = 0
                notes += "Данные на сервере были сброшены — отправляю все свои данные заново."
            }
            for (item in page.items) {
                try {
                    when (item.kind) {
                        KIND_FAVORITE -> applyFavorite(item, fav)
                        KIND_HISTORY -> applyHistory(item, hist)
                        else -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    (if (item.kind == KIND_FAVORITE) fav else hist).errors++
                }
            }
            cursor = page.cursor
            if (!page.hasMore || ++pages >= MAX_PULL_PAGES) break
        }
        prefs.v2Cursor = cursor

        // 2. Отправляем свои изменения: правки новее прошлой отправки + метки удаления.
        val items = ArrayList<SyncPushItemDto>()
        for (f in favDao.getSince(pushSince)) {
            if (f.contentId in fav.appliedIds) continue
            if (!validId(f.contentId)) { fav.errors++; continue }
            fav.sent++
            items += SyncPushItemDto(KIND_FAVORITE, f.contentId, f.updatedAt.coerceAtLeast(0), false, favoriteData(f))
        }
        for (h in histDao.getSince(pushSince)) {
            if (h.contentId in hist.appliedIds) continue
            if (!validId(h.contentId)) { hist.errors++; continue }
            hist.sent++
            items += SyncPushItemDto(KIND_HISTORY, h.contentId, h.watchedAt.coerceAtLeast(0), false, historyData(h))
        }
        for (t in tombDao.getSince(pushSince)) {
            if (!validId(t.contentId)) continue
            (if (t.kind == KIND_FAVORITE) fav else hist).deletedSent++
            items += SyncPushItemDto(t.kind, t.contentId, t.deletedAt.coerceAtLeast(0), true)
        }
        var accepted = 0; var updated = 0; var ignored = 0; var rejected = 0
        for (chunk in items.chunked(PUSH_CHUNK)) {
            val r = api.pushSyncV2(auth, SyncPushRequestV2(chunk))
            accepted += r.accepted; updated += r.updated; ignored += r.ignored; rejected += r.rejected
            r.errors.take(3).forEach { notes += "Сервер отклонил запись: $it" }
        }
        prefs.lastPushAt = pushStartedAt
        tombDao.purgeOlderThan(System.currentTimeMillis() - TOMBSTONE_KEEP_MS)

        // 3. Подтверждение получения: сообщаем серверу, что применили, и сверяем контрольную сумму.
        val ack = try {
            api.ackSyncV2(
                auth,
                SyncAckRequest(
                    cursor = cursor,
                    applied = mapOf(KIND_FAVORITE to fav.appliedCount(), KIND_HISTORY to hist.appliedCount()),
                    checksum = localChecksum()
                )
            )
        } catch (e: HttpException) {
            if (e.code() == 401) throw e
            null
        } catch (e: IOException) {
            null
        }

        return SyncReport(
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            trigger = trigger,
            ok = true,
            favorites = fav.toReport(),
            history = hist.toReport(),
            serverAccepted = accepted, serverUpdated = updated, serverIgnored = ignored, serverRejected = rejected,
            checksumMatches = ack?.matchesServer,
            notes = notes
        )
    }

    // ---------------------------------------------------------------- применение чужих записей

    private suspend fun applyFavorite(item: SyncItemDto, c: Counters) {
        c.received++
        val id = item.contentId
        val existing = favDao.getFavoriteByContentId(id)
        if (item.deleted) {
            if (existing != null && existing.updatedAt <= item.updatedAt) {
                favDao.deleteByContentId(id)
                tombDao.remove(KIND_FAVORITE, id) // иначе удаление вернулось бы на сервер как «наше»
                c.deleted++; c.appliedIds += id
            } else {
                if (existing == null) tombDao.remove(KIND_FAVORITE, id)
                c.skipped++
            }
            return
        }
        if (existing != null && existing.updatedAt >= item.updatedAt) { c.skipped++; return }
        val tomb = tombDao.get(KIND_FAVORITE, id)
        if (tomb != null && tomb.deletedAt > item.updatedAt) { c.skipped++; return } // наше удаление новее
        val d = item.data
        // FavoriteEntity.id — autoGenerate, REPLACE по нему не работает как upsert по contentId:
        // старую запись убираем явно (триггер оставит метку, следующий INSERT её снимет).
        favDao.deleteByContentId(id)
        favDao.insertFavorite(
            FavoriteEntity(
                contentId = id,
                contentType = d.str("contentType") ?: "MOVIE",
                folderId = existing?.folderId, sortOrder = existing?.sortOrder ?: 0, isAnime = existing?.isAnime ?: false,
                title = d.str("title") ?: "", poster = d.str("poster"),
                addedAt = existing?.addedAt ?: if (item.updatedAt > 0) item.updatedAt else System.currentTimeMillis(),
                updatedAt = item.updatedAt
            )
        )
        if (existing == null) c.added++ else c.updated++
        c.appliedIds += id
    }

    private suspend fun applyHistory(item: SyncItemDto, c: Counters) {
        c.received++
        val id = item.contentId
        val existing = histDao.getByContentId(id)
        if (item.deleted) {
            if (existing != null && existing.watchedAt <= item.updatedAt) {
                histDao.deleteByContentId(id)
                tombDao.remove(KIND_HISTORY, id)
                c.deleted++; c.appliedIds += id
            } else {
                if (existing == null) tombDao.remove(KIND_HISTORY, id)
                c.skipped++
            }
            return
        }
        if (existing != null && existing.watchedAt >= item.updatedAt) { c.skipped++; return }
        val tomb = tombDao.get(KIND_HISTORY, id)
        if (tomb != null && tomb.deletedAt > item.updatedAt) { c.skipped++; return }
        val d = item.data
        histDao.upsert(
            WatchHistoryEntity(
                contentId = id, title = d.str("title") ?: "", poster = d.str("poster"),
                positionMs = d.lng("positionMs"), durationMs = d.lng("durationMs"),
                // watchedAt — реальное время просмотра, а не «сейчас» (иначе «Продолжить просмотр» прыгал бы).
                watchedAt = if (item.updatedAt > 0) item.updatedAt else System.currentTimeMillis(),
                completed = d.bool("completed"), seriesId = d.str("seriesId")
            )
        )
        if (existing == null) c.added++ else c.updated++
        c.appliedIds += id
    }

    private fun favoriteData(f: FavoriteEntity) = JsonObject().apply {
        addProperty("contentId", f.contentId)
        addProperty("contentType", f.contentType)
        addProperty("title", f.title)
        f.poster?.let { addProperty("poster", it) }
        addProperty("updatedAt", f.updatedAt)
    }

    private fun historyData(h: WatchHistoryEntity) = JsonObject().apply {
        addProperty("contentId", h.contentId)
        addProperty("title", h.title)
        h.poster?.let { addProperty("poster", it) }
        addProperty("positionMs", h.positionMs)
        addProperty("durationMs", h.durationMs)
        addProperty("completed", h.completed)
        addProperty("updatedAt", h.watchedAt)
        h.seriesId?.let { addProperty("seriesId", it) }
    }

    /** Та же формула, что у сервера (sync_v2.py::_checksum): sha256 строк «вид|id|updatedAt» живых записей. */
    private suspend fun localChecksum(): String {
        val lines = ArrayList<String>()
        favDao.getAllFavorites().first().forEach { lines += "$KIND_FAVORITE|${it.contentId}|${it.updatedAt}" }
        histDao.getRecent(Int.MAX_VALUE).first().forEach { lines += "$KIND_HISTORY|${it.contentId}|${it.watchedAt}" }
        lines.sort()
        val digest = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---------------------------------------------------------------- устройства и сопряжение

    override suspend fun createPairingCode(): Result<PairingCode> = withContext(Dispatchers.IO) {
        safe {
            val dto = authed { api.createPairingCodeV2(it) }
            PairingCode(dto.code, dto.expiresInSeconds)
        }
    }

    override suspend fun redeemPairingCode(code: String): Result<RedeemResult> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            safe {
                val dto = authed { api.redeemPairingCodeV2(it, PairingRedeemV2Request(code = code)) }
                // Устройство переехало в группу владельца кода: старый курсор там недействителен.
                prefs.v2Cursor = 0
                prefs.lastPushAt = 0
                prefs.syncLinksLost = false
                RedeemResult(dto.inviterName, dto.merged)
            }
        }
    }

    override suspend fun devices(): Result<List<DeviceInfo>> = withContext(Dispatchers.IO) {
        safe {
            authed { api.getDevices(it) }.devices.map { d ->
                DeviceInfo(
                    id = d.id, name = d.name, type = d.type, createdAt = d.createdAt,
                    lastSeenAt = d.lastSeenAt, lastSyncAt = d.lastSyncAt, isCurrent = d.isCurrent,
                    ackAt = d.lastAck?.at?.takeIf { it > 0 },
                    ackApplied = d.lastAck?.applied ?: emptyMap(),
                    ackMatchesServer = d.lastAck?.matchesServer
                )
            }
        }
    }

    override suspend fun renameDevice(id: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        safe {
            val clean = name.trim().take(MAX_NAME)
            require(clean.isNotEmpty()) { "Имя не может быть пустым" }
            authed { api.renameDevice(it, id, DeviceRenameRequest(clean)) }
            if (id == prefs.v2DeviceId) prefs.deviceName = clean
        }
    }

    override suspend fun unlinkDevice(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        safe {
            authed { api.unlinkDevice(it, id) }
            if (id == prefs.v2DeviceId) {
                // Отвязали себя: токен больше недействителен. Следующая синхронизация зарегистрирует заново, в новую группу.
                prefs.clearV2Credentials()
                prefs.syncLinksLost = false
            }
        }
    }

    override fun recentReports(): List<SyncReport> = reports.load()

    // ---------------------------------------------------------------- регистрация и авторизация

    private suspend fun ensureRegistered() = registerMutex.withLock {
        if (!prefs.v2Token.isNullOrBlank() && !prefs.v2DeviceId.isNullOrBlank()) return@withLock
        // Данные старой (v1) синхронизации переносим только при самой первой регистрации. После
        // «потери связи» старый идентификатор не передаём: иначе отвязанное устройство
        // вернулось бы в прежнюю группу.
        val legacy = if (prefs.syncLinksLost) null else prefs.syncToken
        val name = currentDeviceName()
        val resp = api.registerDevice(DeviceRegisterRequest(name = name, type = deviceType(), legacyDeviceId = legacy))
        prefs.v2DeviceId = resp.deviceId
        prefs.v2Token = resp.token
        prefs.v2Cursor = 0
        prefs.lastPushAt = 0
        if (prefs.deviceName.isNullOrBlank()) prefs.deviceName = name
        Unit
    }

    private fun bearer(): String = "Bearer ${prefs.v2Token.orEmpty()}"

    private fun markCredentialsLost() {
        val hadCredentials = !prefs.v2Token.isNullOrBlank()
        prefs.clearV2Credentials()
        if (hadCredentials) prefs.syncLinksLost = true
    }

    /** Вызов с токеном; при 401 учётные данные сбрасываются (синхронизация потом зарегистрирует заново). */
    private suspend fun <T> authed(block: suspend (String) -> T): T {
        ensureRegistered()
        try {
            return block(bearer())
        } catch (e: HttpException) {
            if (e.code() == 401) {
                markCredentialsLost()
                throw IllegalStateException("Это устройство больше не привязано на сервере. Нажмите «Синхронизировать сейчас» — оно зарегистрируется заново.")
            }
            throw e
        }
    }

    private fun currentDeviceName(): String {
        prefs.deviceName?.takeIf { it.isNotBlank() }?.let { return it }
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val full = if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank()) model else "$manufacturer $model"
        return full.ifBlank { if (deviceType() == "tv") "Телевизор" else "Телефон" }.take(MAX_NAME)
    }

    private fun deviceType(): String = if (isTV(appContext)) "tv" else "phone"

    // ---------------------------------------------------------------- вспомогательное

    private inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception(friendlyError(e), e))
    }

    private fun validId(id: String) = id.isNotBlank() && id.length <= MAX_ID

    private class Counters {
        var sent = 0; var received = 0; var added = 0; var updated = 0
        var deleted = 0; var skipped = 0; var errors = 0; var deletedSent = 0
        /** contentId записей, которые только что применили из выдачи сервера: обратно их не отправляем. */
        val appliedIds = HashSet<String>()
        fun appliedCount() = added + updated + deleted
        fun toReport() = CategoryReport(sent, received, added, updated, deleted, skipped, errors, deletedSent)
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.lng(key: String): Long =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false

    companion object {
        private const val KIND_FAVORITE = "favorite"
        private const val KIND_HISTORY = "history"
        private const val PUSH_CHUNK = 500
        private const val MAX_PULL_PAGES = 200
        private const val MAX_ID = 256
        private const val MAX_NAME = 64
        private const val TOMBSTONE_KEEP_MS = 90L * 24 * 60 * 60 * 1000

        // Общие на процесс: SessionGraph может пересоздать репозиторий (reinitWithAuth).
        private val syncMutex = Mutex()
        private val registerMutex = Mutex()

        /** Идёт ли синхронизация прямо сейчас (AutoSync не реагирует на её собственные записи в базу). */
        val isRunning: Boolean get() = syncMutex.isLocked

        /** Текст ошибки для пользователя: ответ сервера, если он есть, иначе понятное описание. */
        fun friendlyError(e: Throwable): String = when (e) {
            is HttpException -> serverDetail(e) ?: when (e.code()) {
                429 -> "Слишком много запросов, подождите минуту."
                in 500..599 -> "Сервер временно недоступен (${e.code()}), повторите через минуту."
                else -> "Ошибка сервера (${e.code()})."
            }
            is IOException -> "Нет связи с сервером. Бесплатный сервер может просыпаться до минуты — повторите."
            else -> e.message ?: e.javaClass.simpleName
        }

        private fun serverDetail(e: HttpException): String? = try {
            val body = e.response()?.errorBody()?.string()
            val detail = if (body.isNullOrBlank()) null else JsonParser.parseString(body).asJsonObject.get("detail")
            if (detail != null && detail.isJsonPrimitive) detail.asString else null
        } catch (_: Exception) {
            null
        }
    }
}
