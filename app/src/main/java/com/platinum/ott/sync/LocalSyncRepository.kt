package com.platinum.ott.sync

import android.content.Context
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.InterfacePreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.companion.LocalSyncHttpServer
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.dao.PlaylistSourceDao
import com.platinum.ott.data.local.dao.PluginDao
import com.platinum.ott.data.local.dao.SyncTombstoneDao
import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.data.remote.dto.FavoriteDto
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import com.platinum.ott.data.remote.dto.LocalSyncPluginDto
import com.platinum.ott.data.remote.dto.LocalSyncSettingsDto
import com.platinum.ott.data.remote.dto.LocalSyncSourceDto
import com.platinum.ott.data.remote.dto.WatchHistoryDto
import com.platinum.ott.data.repository.HiddenChannelsStore
import com.platinum.ott.data.repository.PlaylistSourceRepository
import com.platinum.ott.sync.local.AcceptSet
import com.platinum.ott.sync.local.AndroidLocalIdentity
import com.platinum.ott.sync.local.ApplyReport
import com.platinum.ott.sync.local.CategoryResult
import com.platinum.ott.sync.local.EncryptedPairedStore
import com.platinum.ott.sync.local.Endpoint
import com.platinum.ott.sync.local.LocalIdentity
import com.platinum.ott.sync.local.LocalProto
import com.platinum.ott.sync.local.LocalSyncClient
import com.platinum.ott.sync.local.LocalSyncEngine
import com.platinum.ott.sync.local.LocalSyncException
import com.platinum.ott.sync.local.Manifest
import com.platinum.ott.sync.local.NearbyDiscovery
import com.platinum.ott.sync.local.NearbyPeer
import com.platinum.ott.sync.local.OkHttpTransport
import com.platinum.ott.sync.local.PairedDevice
import com.platinum.ott.sync.local.PairedStore
import com.platinum.ott.sync.local.ReceiverEvents
import com.platinum.ott.sync.local.ReceiverHost
import com.platinum.ott.sync.local.SendResult
import com.platinum.ott.sync.local.SendStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Адрес этого устройства для ручного ввода на другом: «192.168.1.5:37221». */
data class NearbyInfo(val ip: String?, val port: Int) {
    val address: String? get() = ip?.let { "$it:$port" }
}

/**
 * Локальная синхронизация по Wi-Fi, протокол v2 (см. sync/local/LocalProto.kt): приём и отправка между
 * устройствами рядом, с шифрованием, подтверждением на получателе и отчётом. Здесь — связка протокола
 * с базой приложения: сборка исходящего набора и применение входящего (по времени изменения, а не «в лоб»).
 * Независим от серверной синхронизации (SyncRepositoryImpl): переносит и то, чего сервер не хранит
 * (источники, плагины, настройки).
 */
class LocalSyncRepository(
    private val appContext: Context,
    private val favoritesDao: FavoritesDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val playlistSourceDao: PlaylistSourceDao,
    private val playlistSourceRepository: PlaylistSourceRepository,
    private val pluginDao: PluginDao,
    private val pluginManager: PluginManager,
    private val pluginRepository: PluginRepository,
    private val networkPreferences: NetworkPreferences,
    private val notificationPreferences: NotificationPreferences,
    private val hiddenChannelsStore: HiddenChannelsStore,
    private val tombDao: SyncTombstoneDao,
    authPreferences: AuthPreferences
) {
    val identity: LocalIdentity = AndroidLocalIdentity(appContext, authPreferences)
    val pairedStore: PairedStore = EncryptedPairedStore(appContext)
    private val discovery = NearbyDiscovery(appContext, identity)
    private val client = LocalSyncClient(identity, pairedStore, OkHttpTransport())

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val interfacePreferences by lazy { InterfacePreferences(appContext) }
    private val qualityPreferences by lazy { QualityPreferences(appContext) }
    private val subtitlePreferences by lazy { SubtitlePreferences(appContext) }

    private var engine: LocalSyncEngine? = null
    private var server: LocalSyncHttpServer? = null

    /** Устройства, найденные рядом (NSD). */
    val peers: StateFlow<List<NearbyPeer>> get() = discovery.peers

    // ---------------------------------------------------------------- приём: сервер, поиск, код

    /** Запускает приём: сервер, объявление в сети и поиск остальных. Вызывается, пока открыт раздел «Мои устройства». */
    @Synchronized
    fun startNearby(events: ReceiverEvents): NearbyInfo? {
        stopNearby()
        val host = object : ReceiverHost, ReceiverEvents by events {
            override suspend fun applyIncoming(device: PairedDevice, accept: AcceptSet, payload: LocalSyncPayload): ApplyReport =
                applyPayload(payload)
        }
        val eng = LocalSyncEngine(identity, pairedStore, host)
        var srv = LocalSyncHttpServer(LocalProto.DEFAULT_PORT, eng)
        val port = try {
            srv.startServer()
        } catch (_: Exception) {
            // Порт занят — берём любой свободный (NSD всё равно сообщит его, а вручную можно ввести IP:порт).
            srv = LocalSyncHttpServer(0, eng)
            try { srv.startServer() } catch (_: Exception) { return null }
        }
        engine = eng
        server = srv
        discovery.start(port)
        return NearbyInfo(LocalNetworkUtils.getLocalIpAddress(), port)
    }

    @Synchronized
    fun stopNearby() {
        discovery.stop()
        try { server?.stop() } catch (_: Exception) {}
        engine?.shutdown()
        server = null
        engine = null
    }

    fun engine(): LocalSyncEngine? = engine

    fun pairedDevices(): List<PairedDevice> = pairedStore.all()

    fun forget(deviceId: String) = pairedStore.remove(deviceId)

    // ---------------------------------------------------------------- отправка и связывание (клиент)

    fun client(): LocalSyncClient = client

    /** Предлагает связанному устройству свои данные; что именно передать, решает получатель. */
    suspend fun sendTo(
        endpoint: Endpoint,
        device: PairedDevice,
        progress: (SendStage) -> Unit
    ): Result<SendResult> {
        val payload = try {
            buildPayload()
        } catch (e: Exception) {
            return Result.failure(LocalSyncException("Не удалось собрать данные: ${e.message}"))
        }
        val manifest = Manifest.of(payload)
        if (manifest.isEmpty()) return Result.failure(LocalSyncException("Нечего отправлять: избранное, история и источники пусты."))
        return client.send(endpoint, device, manifest, { payload }, progress)
    }

    // ---------------------------------------------------------------- сборка исходящего набора

    suspend fun buildPayload(): LocalSyncPayload = withContext(Dispatchers.IO) {
        val favorites = favoritesDao.getAllFavorites().first().map {
            FavoriteDto(contentId = it.contentId, contentType = it.contentType, title = it.title, poster = it.poster, updatedAt = it.updatedAt)
        }
        val history = watchHistoryDao.getSince(0).map {
            WatchHistoryDto(
                contentId = it.contentId, title = it.title, poster = it.poster,
                positionMs = it.positionMs, durationMs = it.durationMs, completed = it.completed,
                updatedAt = it.watchedAt, seriesId = it.seriesId
            )
        }
        val sources = playlistSourceRepository.getAll()
            // «file://» — локальный снапшот файла, на другом устройстве он недоступен.
            .filterNot { it.url?.startsWith("file://") == true }
            .map {
                LocalSyncSourceDto(
                    id = it.id, type = it.type, label = it.label, url = it.url,
                    host = it.host, username = it.username, password = it.password,
                    enabled = it.enabled, priority = it.priority,
                    contentKind = it.contentKind, groupOverrides = it.groupOverrides
                )
            }
        val plugins = pluginDao.getAll().first().map {
            LocalSyncPluginDto(
                id = it.id, name = it.name, repoUrl = it.repoUrl,
                scriptContent = if (it.repoUrl.isBlank()) it.scriptContent else "",
                isEnabled = it.isEnabled
            )
        }
        val settings = LocalSyncSettingsDto(
            darkTheme = interfacePreferences.isDarkTheme,
            maxQualityOnMobile = qualityPreferences.getMaxQualityOnMobile(),
            networkTimeoutSeconds = networkPreferences.getTimeoutSeconds(),
            newEpisodesNotificationsEnabled = notificationPreferences.isNewEpisodesEnabled(),
            subtitlesShowByDefault = subtitlePreferences.getShowByDefault()
        )
        LocalSyncPayload(
            favorites = favorites, watchHistory = history, settings = settings,
            plugins = plugins, sources = sources, hiddenItems = hiddenChannelsStore.snapshot(),
            deviceTimestamp = System.currentTimeMillis()
        )
    }

    // ---------------------------------------------------------------- применение входящего набора

    /**
     * Применяет уже отфильтрованный по разрешению набор. Избранное и история — по времени изменения
     * (новее выигрывает; своё удаление, более позднее чем присланная запись, не отменяется).
     * Удаления по Wi-Fi не передаются: их переносит серверная синхронизация.
     * Ошибка одной записи не прерывает остальные — она попадает в отчёт с причиной.
     */
    suspend fun applyPayload(p: LocalSyncPayload): ApplyReport = withContext(Dispatchers.IO) {
        val cats = LinkedHashMap<String, CategoryResult>()
        val notes = ArrayList<String>()
        val errors = ArrayList<String>()

        // ---- избранное
        run {
            var added = 0; var updated = 0; var skipped = 0; var failed = 0
            for (dto in p.favorites) {
                try {
                    if (dto.contentId.isBlank()) { failed++; continue }
                    val existing = favoritesDao.getFavoriteByContentId(dto.contentId)
                    val stamp = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis()
                    if (existing != null && existing.updatedAt >= stamp) { skipped++; continue }
                    val tomb = tombDao.get("favorite", dto.contentId)
                    if (tomb != null && tomb.deletedAt > stamp) { skipped++; continue }
                    // contentId не первичный ключ: старую запись убираем явно (триггер оставит метку, вставка её снимет).
                    favoritesDao.deleteByContentId(dto.contentId)
                    favoritesDao.insertFavorite(
                        FavoriteEntity(
                            contentId = dto.contentId, contentType = dto.contentType, title = dto.title,
                            folderId = existing?.folderId, sortOrder = existing?.sortOrder ?: 0,
                            isAnime = existing?.isAnime ?: false,
                            poster = dto.poster, addedAt = existing?.addedAt ?: stamp, updatedAt = stamp
                        )
                    )
                    if (existing == null) added++ else updated++
                } catch (e: Exception) {
                    failed++
                    if (errors.size < MAX_LISTED_ERRORS) errors += "Избранное «${dto.title.ifBlank { dto.contentId }}»: ${e.message ?: "ошибка"}"
                }
            }
            cats[LocalProto.CAT_FAVORITES] = CategoryResult(added = added, updated = updated, skipped = skipped, errors = failed)
        }

        // ---- история
        run {
            var added = 0; var updated = 0; var skipped = 0; var failed = 0
            val toWrite = ArrayList<WatchHistoryEntity>()
            for (chunk in p.watchHistory.chunked(500)) {
                try {
                    val local = watchHistoryDao.getByContentIds(chunk.map { it.contentId }).associateBy { it.contentId }
                    for (dto in chunk) {
                        if (dto.contentId.isBlank()) { failed++; continue }
                        val stamp = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis()
                        val existing = local[dto.contentId]
                        if (existing != null && existing.watchedAt >= stamp) { skipped++; continue }
                        val tomb = tombDao.get("history", dto.contentId)
                        if (tomb != null && tomb.deletedAt > stamp) { skipped++; continue }
                        toWrite += WatchHistoryEntity(
                            contentId = dto.contentId, title = dto.title, poster = dto.poster,
                            positionMs = dto.positionMs, durationMs = dto.durationMs,
                            watchedAt = stamp, completed = dto.completed, seriesId = dto.seriesId
                        )
                        if (existing == null) added++ else updated++
                    }
                } catch (e: Exception) {
                    failed += chunk.size
                    if (errors.size < MAX_LISTED_ERRORS) errors += "История: ${e.message ?: "ошибка"}"
                }
            }
            try {
                if (toWrite.isNotEmpty()) watchHistoryDao.upsertAll(toWrite)
            } catch (e: Exception) {
                failed += toWrite.size; added = 0; updated = 0
                if (errors.size < MAX_LISTED_ERRORS) errors += "История: не удалось записать — ${e.message ?: "ошибка"}"
            }
            cats[LocalProto.CAT_HISTORY] = CategoryResult(added = added, updated = updated, skipped = skipped, errors = failed)
        }

        // ---- источники (только добавление/обновление, локальные не удаляются)
        run {
            var added = 0; var updated = 0; var failed = 0
            var touched = false
            if (p.sources.isNotEmpty()) {
                val local = playlistSourceRepository.getAll().toMutableList()
                for (dto in p.sources) {
                    try {
                        val match = local.firstOrNull { it.id == dto.id } ?: local.firstOrNull { sameSource(it, dto) }
                        if (dto.needsCredentials == true) {
                            if (match != null) {
                                // Данные входа у нас уже есть — их не затираем, обновляем только описание.
                                val upd = match.copy(
                                    label = dto.label, priority = dto.priority,
                                    contentKind = dto.contentKind ?: match.contentKind,
                                    groupOverrides = dto.groupOverrides ?: match.groupOverrides
                                )
                                playlistSourceDao.upsert(upd)
                                local[local.indexOf(match)] = upd
                                updated++
                            } else {
                                val fresh = newSource(dto, enabled = false)
                                playlistSourceDao.upsert(fresh)
                                local += fresh
                                added++
                                notes += "Источник «${dto.label}» добавлен выключенным: нужны данные входа. Откройте «Источники» и введите логин и пароль."
                            }
                        } else {
                            val entity = newSource(dto, enabled = dto.enabled).let { n ->
                                if (match == null) n else n.copy(
                                    id = match.id, lastRefreshedAt = match.lastRefreshedAt, lastRefreshStatus = match.lastRefreshStatus,
                                    legacyIds = match.legacyIds, epgUrl = match.epgUrl
                                )
                            }
                            playlistSourceDao.upsert(entity)
                            if (match == null) { local += entity; added++ } else { local[local.indexOf(match)] = entity; updated++ }
                            if (entity.enabled) touched = true
                        }
                    } catch (e: Exception) {
                        failed++
                        if (errors.size < MAX_LISTED_ERRORS) errors += "Источник «${dto.label}»: ${e.message ?: "ошибка"}"
                    }
                }
            }
            // Подтягиваем контент в фоне: ответ получателя не должен ждать обновления источников.
            if (touched) backgroundScope.launch { playlistSourceRepository.refreshAll() }
            cats[LocalProto.CAT_SOURCES] = CategoryResult(added = added, updated = updated, errors = failed)
        }

        // ---- плагины
        run {
            var added = 0; var updated = 0; var failed = 0
            for (dto in p.plugins) {
                try {
                    val existed = pluginDao.getById(dto.id) != null
                    val result = when {
                        dto.repoUrl.isNotBlank() -> pluginRepository.installFromUrl(dto.repoUrl)
                        dto.scriptContent.isNotBlank() -> pluginManager.installPlugin(dto.scriptContent)
                        else -> { failed++; errors += "Плагин «${dto.name}»: нет ни ссылки, ни кода"; continue }
                    }
                    val manifest = result.getOrNull()
                    if (manifest == null) {
                        failed++
                        errors += "Плагин «${dto.name}»: ${result.exceptionOrNull()?.message ?: "не удалось установить"}"
                        continue
                    }
                    if (!dto.isEnabled) pluginManager.setEnabled(manifest.id.replace(Regex("[^a-zA-Z0-9_-]"), "_"), false)
                    if (existed) updated++ else added++
                } catch (e: Exception) {
                    failed++
                    errors += "Плагин «${dto.name}»: ${e.message ?: "ошибка"}"
                }
            }
            cats[LocalProto.CAT_PLUGINS] = CategoryResult(added = added, updated = updated, errors = failed)
        }

        // ---- настройки (перезаписываются: это явно разрешено получателем)
        run {
            val s = p.settings
            if (s != null) {
                try {
                    interfacePreferences.isDarkTheme = s.darkTheme
                    qualityPreferences.setMaxQualityOnMobile(s.maxQualityOnMobile)
                    networkPreferences.setTimeoutSeconds(s.networkTimeoutSeconds)
                    notificationPreferences.setNewEpisodesEnabled(s.newEpisodesNotificationsEnabled)
                    subtitlePreferences.setShowByDefault(s.subtitlesShowByDefault)
                    cats[LocalProto.CAT_SETTINGS] = CategoryResult(updated = 1)
                } catch (e: Exception) {
                    errors += "Настройки: ${e.message ?: "ошибка"}"
                    cats[LocalProto.CAT_SETTINGS] = CategoryResult(errors = 1)
                }
            }
        }

        // ---- скрытые каналы и категории
        run {
            val items = p.hiddenItems
            if (items != null) {
                try {
                    val changed = hiddenChannelsStore.merge(items)
                    cats[LocalProto.CAT_HIDDEN] = if (changed) CategoryResult(updated = items.size) else CategoryResult(skipped = items.size)
                } catch (e: Exception) {
                    errors += "Скрытые каналы: ${e.message ?: "ошибка"}"
                    cats[LocalProto.CAT_HIDDEN] = CategoryResult(errors = items.size)
                }
            }
        }

        ApplyReport(cats, notes, errors)
    }

    private fun newSource(dto: LocalSyncSourceDto, enabled: Boolean) = PlaylistSourceEntity(
        id = dto.id, type = dto.type, label = dto.label, url = dto.url,
        host = dto.host, username = dto.username, password = dto.password,
        enabled = enabled, priority = dto.priority,
        contentKind = dto.contentKind ?: "vod", groupOverrides = dto.groupOverrides ?: "{}"
    )

    /** Тот же источник под другим id (добавлен вручную на обоих устройствах): совпадает адрес / панель и логин. */
    private fun sameSource(local: PlaylistSourceEntity, dto: LocalSyncSourceDto): Boolean {
        if (local.type != dto.type) return false
        fun norm(s: String?) = s?.trim()?.trimEnd('/')?.lowercase().orEmpty()
        return if (dto.type == "xtream") {
            val h = norm(dto.host)
            h.isNotEmpty() && h == norm(local.host) && (dto.username.isNullOrBlank() || dto.username == local.username)
        } else {
            val u = norm(dto.url)
            u.isNotEmpty() && (u == norm(local.url) || u == norm(local.url?.substringBefore('?')))
        }
    }

    private companion object {
        const val MAX_LISTED_ERRORS = 10
    }
}
