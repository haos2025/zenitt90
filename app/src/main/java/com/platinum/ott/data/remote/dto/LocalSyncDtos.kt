package com.platinum.ott.data.remote.dto

/**
 * PROMPT_LOCAL_SYNC_V1.md — DTO для локального канала (без бэкенда), а не
 * расширение SyncDtos.kt выше в этом файле: набор данных шире (настройки,
 * плагины, источники — то, чего /sync на бэкенде не знает вообще, см.
 * SyncRepositoryImpl.kt), и это разные, независимые каналы синхронизации
 * (можно использовать один без другого). FavoriteDto/WatchHistoryDto
 * переиспользованы как есть — избранное и история передаются в том же виде,
 * что и на бэкенд, расширять именно эти два формата (папки, isAnime) в этой
 * сессии не входило в задачу.
 */

// Настройки уровня приложения (core/InterfacePreferences.kt и соседние
// *Preferences классы) — то, что раньше синхронизировать было нечем: у
// бэкенда своих настроек нет вообще.
data class LocalSyncSettingsDto(
    val darkTheme: Boolean = true,
    val maxQualityOnMobile: String = "720p",
    val networkTimeoutSeconds: Int = 15,
    val newEpisodesNotificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 8,
    val subtitlesShowByDefault: Boolean = false
)

// Один установленный плагин. Решение из PROMPT_LOCAL_SYNC_V1.md: передавать
// URL источника плагина, а не бинарник/скрипт целиком — на принимающем
// устройстве переустановка идёт через уже существующий
// PluginRepository.installFromUrl(). scriptContent заполняется ТОЛЬКО когда
// repoUrl пуст — то есть плагин был установлен через installFromScript()
// (PluginViewModel.kt) без URL вообще, PluginDao.repoUrl в этом случае "" по
// умолчанию (см. PluginManager.installPlugin()) и переустановить по ссылке
// физически нечем. Это ровно тот случай, для которого промт прямо разрешил
// решить на месте, "смотря что реально хранится в PluginDao" — там
// scriptContent хранится всегда (не только при таком варианте установки),
// так что просто используем его как честный фолбэк.
data class LocalSyncPluginDto(
    val id: String,
    val name: String,
    val repoUrl: String = "",
    val scriptContent: String = "",
    val isEnabled: Boolean = true
)

// Один источник (PlaylistSourceEntity) — задача "Источники" уже переводит
// это в список, мультиплейлист на момент этой сессии подтверждён как
// реализованный, поэтому синхронизируется весь список, не один активный
// источник (альтернатива, описанная в самом промте на случай, если
// мультиплейлиста ещё нет). url со схемой "file://" (локальный снапшот
// M3U-файла, импортированный через ACTION_OPEN_DOCUMENT — см.
// PlaylistSourceEntity.kt) сознательно НЕ включается в исходящий payload:
// содержимое файла на другом устройстве недоступно, а передавать сам файл
// целиком в эту сессию не входило — LocalSyncRepository просто пропускает
// такие источники при сборке payload.
data class LocalSyncSourceDto(
    val id: String,
    val type: String,
    val label: String,
    val url: String? = null,
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0
)

data class LocalSyncPayload(
    val favorites: List<FavoriteDto> = emptyList(),
    val watchHistory: List<WatchHistoryDto> = emptyList(),
    val settings: LocalSyncSettingsDto? = null,
    val plugins: List<LocalSyncPluginDto> = emptyList(),
    val sources: List<LocalSyncSourceDto> = emptyList(),
    val deviceTimestamp: Long = 0
)

// Тело POST /local_sync. Код проверяется ДО разбора payload как данных
// (см. LocalSyncHttpServer.kt) — по решению из промта, защита кодом, а не
// "доверяем всем в моей Wi-Fi".
data class LocalSyncRequestDto(
    val code: String = "",
    val payload: LocalSyncPayload = LocalSyncPayload()
)

// Ответ TV — уже смёрженный (после применения присланного payload) снапшот
// TV, а не просто "OK". Один HTTP-запрос с телефона так закрывает и push, и
// pull разом — см. обоснование в LocalSyncHttpServer.kt.
data class LocalSyncResponseDto(
    val payload: LocalSyncPayload? = null,
    val error: String? = null
)
