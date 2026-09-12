package com.platinum.ott.sync

import android.content.Context
import com.google.gson.Gson
import com.platinum.ott.core.InterfacePreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.core.companion.LocalSyncCode
import com.platinum.ott.core.companion.LocalSyncHttpServer
import com.platinum.ott.core.companion.LocalSyncPairingCode
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.dao.PluginDao
import com.platinum.ott.data.local.dao.WatchHistoryDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.PlaylistSourceDao
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.data.remote.dto.FavoriteDto
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import com.platinum.ott.data.remote.dto.LocalSyncPluginDto
import com.platinum.ott.data.remote.dto.LocalSyncRequestDto
import com.platinum.ott.data.remote.dto.LocalSyncResponseDto
import com.platinum.ott.data.remote.dto.LocalSyncSettingsDto
import com.platinum.ott.data.remote.dto.LocalSyncSourceDto
import com.platinum.ott.data.remote.dto.WatchHistoryDto
import com.platinum.ott.data.repository.PlaylistSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * PROMPT_LOCAL_SYNC_V1.md — сборка/применение payload'а локальной
 * синхронизации и обе стороны обмена (TV поднимает сервер, телефон
 * подключается к нему). Дополняет sync/SyncRepositoryImpl.kt (бэкенд-канал),
 * не заменяет его — оба канала независимы, см. обоснование в
 * LocalSyncDtos.kt.
 *
 * *Preferences здесь создаются напрямую с appContext, а не через Hilt —
 * тем же способом, каким QualityPreferences/SubtitlePreferences уже
 * создаются в PlayerViewModel.kt/SettingsScreen.kt (эти два класса не
 * заведены в PreferencesModule.kt). networkPreferences/notificationPreferences
 * — уже Hilt-синглтоны, переданы конструктором из SessionGraph, как и в
 * остальных местах графа.
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
    private val notificationPreferences: NotificationPreferences
) {
    private val gson = Gson()
    private val interfacePreferences by lazy { InterfacePreferences(appContext) }
    private val qualityPreferences by lazy { QualityPreferences(appContext) }
    private val subtitlePreferences by lazy { SubtitlePreferences(appContext) }

    // Короткоживущий клиент без авторизации — тот же принцип, что и у
    // клиента в PhoneQrScanScreen.kt (локальная сеть, не бэкенд-API).
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private var server: LocalSyncHttpServer? = null
    private var activeCode: LocalSyncCode? = null

    // ---------- Сторона TV: поднять сервер, показать код ----------

    // onApplied — вызывается ПОСЛЕ успешного применения payload'а с телефона,
    // на главном потоке (см. Handler.post ниже — тот же приём, что и у
    // CompanionHttpServer в PlayerScreen.kt/SearchScreen.kt: NanoHTTPD
    // работает в своём потоке, Compose-состояние трогать оттуда напрямую
    // нельзя). LocalSyncViewModel передаёт сюда обновление tvState.
    fun startTvServer(onApplied: (LocalSyncPayload) -> Unit = {}): LocalSyncCode {
        stopTvServer()
        val code = LocalSyncPairingCode.generate()
        activeCode = code
        server = LocalSyncHttpServer(
            isCodeValid = { entered -> activeCode?.let { it.code == entered && it.isValidNow() } == true },
            onPayloadReceived = { payload ->
                // NanoHTTPD вызывает serve() в собственном рабочем потоке —
                // не suspend. runBlocking здесь — тот же приём, что и
                // SessionGraph.initAuth() (миграция источников), а не
                // изобретённый заново для этого файла.
                val merged = runBlocking(Dispatchers.IO) { applyPayload(payload) }
                android.os.Handler(android.os.Looper.getMainLooper()).post { onApplied(merged) }
                merged
            }
        )
        server?.startServer()
        return code
    }

    fun stopTvServer() {
        server?.stop()
        server = null
        activeCode = null
    }

    fun tvServerPort(): Int? = server?.listeningPort

    // ---------- Сторона телефона: подключиться к TV, обменяться payload'ом ----------

    // baseUrl — то, что телефон получает из QR (см. LocalSyncScreen.kt на
    // TV): "http://<ip>:<port>". Код вводится отдельно, вручную (см.
    // обоснование выбора в PROMPT_LOCAL_SYNC_V1.md — защита кодом, а не
    // просто "телефон отсканировал QR").
    suspend fun syncWithTv(baseUrl: String, code: String): Result<LocalSyncPayload> = withContext(Dispatchers.IO) {
        try {
            val outgoing = buildLocalPayload()
            val requestDto = LocalSyncRequestDto(code = code, payload = outgoing)
            val body = gson.toJson(requestDto).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + LocalSyncHttpServer.ENDPOINT_PATH)
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val responseBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val parsedError = try {
                        gson.fromJson(responseBody, LocalSyncResponseDto::class.java).error
                    } catch (_: Exception) {
                        null
                    }
                    return@withContext Result.failure(Exception(parsedError ?: "HTTP ${resp.code}"))
                }
                val responseDto = gson.fromJson(responseBody, LocalSyncResponseDto::class.java)
                val merged = responseDto.payload ?: return@withContext Result.failure(Exception("Пустой ответ TV"))
                applyPayload(merged)
                Result.success(merged)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- Сборка исходящего payload'а ----------

    suspend fun buildLocalPayload(): LocalSyncPayload = withContext(Dispatchers.IO) {
        val favorites = favoritesDao.getAllFavorites().first().map {
            FavoriteDto(contentId = it.contentId, contentType = it.contentType, title = it.title, poster = it.poster, updatedAt = it.addedAt)
        }
        val history = watchHistoryDao.getSince(0).map {
            WatchHistoryDto(
                contentId = it.contentId, title = it.title, poster = it.poster,
                positionMs = it.positionMs, durationMs = it.durationMs, completed = it.completed,
                updatedAt = it.watchedAt, seriesId = it.seriesId
            )
        }
        val sources = playlistSourceRepository.getAll()
            // "file://" — локальный снапшот, недоступный на другом
            // устройстве (см. LocalSyncDtos.kt). Пропускаем осознанно.
            .filterNot { it.url?.startsWith("file://") == true }
            .map {
                LocalSyncSourceDto(
                    id = it.id, type = it.type, label = it.label, url = it.url,
                    host = it.host, username = it.username, password = it.password,
                    enabled = it.enabled, priority = it.priority
                )
            }
        val plugins = pluginDao.getAll().first().map {
            LocalSyncPluginDto(
                id = it.id, name = it.name,
                repoUrl = it.repoUrl,
                // scriptContent только когда URL-а нет — см. обоснование в
                // LocalSyncDtos.kt.
                scriptContent = if (it.repoUrl.isBlank()) it.scriptContent else "",
                isEnabled = it.isEnabled
            )
        }
        val settings = LocalSyncSettingsDto(
            darkTheme = interfacePreferences.isDarkTheme,
            maxQualityOnMobile = qualityPreferences.getMaxQualityOnMobile(),
            networkTimeoutSeconds = networkPreferences.getTimeoutSeconds(),
            newEpisodesNotificationsEnabled = notificationPreferences.isNewEpisodesEnabled(),
            quietHoursEnabled = notificationPreferences.isQuietHoursEnabled(),
            quietStartHour = notificationPreferences.getQuietStartHour(),
            quietEndHour = notificationPreferences.getQuietEndHour(),
            subtitlesShowByDefault = subtitlePreferences.getShowByDefault()
        )
        LocalSyncPayload(
            favorites = favorites, watchHistory = history, settings = settings,
            plugins = plugins, sources = sources, deviceTimestamp = System.currentTimeMillis()
        )
    }

    // ---------- Применение входящего payload'а ----------

    // Возвращает свежий локальный снапшот ПОСЛЕ применения — нужен и TV
    // (ответ на запрос телефона), и телефону как финальный результат.
    suspend fun applyPayload(payload: LocalSyncPayload): LocalSyncPayload = withContext(Dispatchers.IO) {
        // Избранное — тот же приём дедупликации, что и в
        // SyncRepositoryImpl.kt (contentId не первичный ключ у FavoriteEntity,
        // insertFavorite() с REPLACE сам по себе дедуп не даёт).
        for (dto in payload.favorites) {
            favoritesDao.deleteByContentId(dto.contentId)
            favoritesDao.insertFavorite(
                FavoriteEntity(
                    contentId = dto.contentId, contentType = dto.contentType, title = dto.title,
                    poster = dto.poster, addedAt = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis(),
                    updatedAt = if (dto.updatedAt > 0) dto.updatedAt else System.currentTimeMillis()
                )
            )
        }

        // История — contentId уже первичный ключ (WatchHistoryEntity),
        // upsertAll с REPLACE безопасен напрямую.
        if (payload.watchHistory.isNotEmpty()) {
            watchHistoryDao.upsertAll(payload.watchHistory.map {
                WatchHistoryEntity(
                    contentId = it.contentId, title = it.title, poster = it.poster,
                    positionMs = it.positionMs, durationMs = it.durationMs,
                    watchedAt = if (it.updatedAt > 0) it.updatedAt else System.currentTimeMillis(),
                    completed = it.completed, seriesId = it.seriesId
                )
            })
        }

        // Источники — только добавление/обновление по id, никогда не
        // удаляем локальные источники, которых нет в присланном списке.
        // legacyIds сознательно не переносится (остаётся false по
        // умолчанию) — синхронизированная копия не является тем самым
        // мигрированным из AuthPreferences источником на ЭТОМ устройстве
        // (см. PlaylistSourceEntity.kt про legacyIds); lastRefreshedAt/
        // lastRefreshStatus тоже не переносятся, получающее устройство
        // обновит их само при следующем refresh().
        if (payload.sources.isNotEmpty()) {
            for (dto in payload.sources) {
                playlistSourceDao.upsert(
                    PlaylistSourceEntity(
                        id = dto.id, type = dto.type, label = dto.label, url = dto.url,
                        host = dto.host, username = dto.username, password = dto.password,
                        enabled = dto.enabled, priority = dto.priority
                    )
                )
            }
            // Подтягиваем контент новых/обновлённых источников сразу, а не
            // ждём случайного следующего TTL-обновления ленты.
            playlistSourceRepository.refreshAll()
        }

        // Плагины — по решению из PROMPT_LOCAL_SYNC_V1.md: URL есть —
        // переустанавливаем по ссылке (installFromUrl скачает актуальную
        // версию сам); URL-а нет — ставим из присланного текста скрипта
        // напрямую (installPlugin с repoUrl="", тот же путь, что и
        // installFromScript() в PluginViewModel.kt). Ошибка одного плагина
        // не должна прерывать перенос остальных данных.
        for (dto in payload.plugins) {
            try {
                if (dto.repoUrl.isNotBlank()) {
                    pluginRepository.installFromUrl(dto.repoUrl)
                } else if (dto.scriptContent.isNotBlank()) {
                    pluginManager.installPlugin(dto.scriptContent)
                }
                // dto.id — id, вычисленный ИЗ manifest.id на устройстве-
                // источнике (PluginManager.installPlugin: safeId). Верно
                // предполагаем, что тот же скрипт даёт тот же safeId и на
                // этом устройстве — не проверяем отдельно; если это когда-
                // нибудь не так (manifest.id менялся между версиями скрипта),
                // setEnabled() ниже просто не найдёт запись и молча ничего
                // не сделает (PluginDao.setEnabled по несуществующему id).
                if (!dto.isEnabled) pluginManager.setEnabled(dto.id, false)
            } catch (_: Exception) {
                // Пропускаем этот плагин, продолжаем перенос остального —
                // сеть/некорректный скрипт на одном плагине не должны
                // срывать весь обмен.
            }
        }

        // Настройки — применяются последними и безусловно перезаписывают
        // локальные значения, как и остальные разделы payload'а (это
        // синхронизация "последний присланный побеждает", как и у
        // бэкенд-канала — см. SyncRepositoryImpl.kt, там то же самое).
        payload.settings?.let { settings ->
            interfacePreferences.isDarkTheme = settings.darkTheme
            qualityPreferences.setMaxQualityOnMobile(settings.maxQualityOnMobile)
            networkPreferences.setTimeoutSeconds(settings.networkTimeoutSeconds)
            notificationPreferences.setNewEpisodesEnabled(settings.newEpisodesNotificationsEnabled)
            notificationPreferences.setQuietHoursEnabled(settings.quietHoursEnabled)
            notificationPreferences.setQuietHours(settings.quietStartHour, settings.quietEndHour)
            subtitlePreferences.setShowByDefault(settings.subtitlesShowByDefault)
        }

        buildLocalPayload()
    }
}
