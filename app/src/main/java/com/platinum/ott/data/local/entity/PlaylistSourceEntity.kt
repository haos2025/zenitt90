package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * До этой сущности источник был один-единственный, конфиг которого целиком
 * жил в AuthPreferences (encrypted SharedPreferences) — type/host/username/
 * password/m3uUrl. Задача "Источники" (PROMPT_SOURCES_SCREEN.md) переводит
 * это в полноценный список: один пользователь может подключить несколько
 * плейлистов/панелей одновременно, контент из них агрегируется в общую
 * ленту (PlaylistRepository.getCatalog(), следующая подзадача).
 *
 * AuthPreferences не удаляется этой миграцией — syncToken/lastSyncTimestamp
 * там остаются (это device-level поля для /sync, не конфиг источника).
 * Только type/host/username/password/m3uUrl станут историческими полями,
 * которые нужны один раз — для разовой миграции существующего пользователя
 * в первую запись PlaylistSource (отдельная подзадача, ещё не сделана).
 *
 * `url` намеренно один и тот же для двух разных способов добавления M3U:
 * - "http://..."/"https://..." — обычная ссылка, PlaylistSourceRepository
 *   будет её фетчить по сети при каждом refresh() (как сейчас).
 * - "file://..." — локальный файл, скопированный в filesDir при добавлении
 *   через ACTION_OPEN_DOCUMENT (решение сессии: копируем содержимое один
 *   раз при импорте, а не держим persistable URI-permission на content://
 *   и не перечитываем внешний файл при каждом refresh()). Обновление —
 *   только через повторный реимпорт пользователем.
 * Отдельного поля-дискриминатора под это не заводим — схема URL уже
 * однозначно говорит, сетевой это источник или локальный снапшот.
 */
@Entity(tableName = "playlist_sources")
data class PlaylistSourceEntity(
    @PrimaryKey val id: String,
    // "m3u" | "xtream"
    val type: String,
    // Имя, которое видит пользователь в списке источников (не техническое)
    val label: String,
    // M3U: ссылка ("http://...") или локальный снапшот ("file://...").
    // Xtream: null, используются host/username/password ниже.
    val url: String? = null,
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    // Выключенный источник не участвует в агрегации каталога и не
    // обновляется по TTL, но остаётся в списке (не путать с удалением).
    val enabled: Boolean = true,
    // Порядок в списке и порядок объединения контента при агрегации.
    // Меньше — выше. Управляется кнопками вверх/вниз в UI (не drag-and-drop,
    // см. обоснование в PROMPT_SOURCES_SCREEN.md — на TV пультом ненадёжно).
    val priority: Int = 0,
    // null = ни разу не обновлялся с момента добавления
    val lastRefreshedAt: Long? = null,
    // "ok" при успехе, иначе текст ошибки для показа в карточке источника.
    // null = обновление ещё не запускалось ни разу.
    val lastRefreshStatus: String? = null,
    // true ТОЛЬКО у источника, созданного разовой миграцией из старого
    // AuthPreferences (PlaylistSourceRepository.migrateLegacySourceIfNeeded()).
    // M3uPlaylistParser/XtreamVodClient генерируют id без привязки к
    // источнику ("m3u_0", "xt_123"...) — при нескольких источниках это
    // коллизия (второй M3U тоже начнёт с "m3u_0"), поэтому для НОВЫХ
    // источников PlaylistSourceRepository.refresh() добавляет префикс
    // sourceId к id/seriesId. Для мигрированного источника префикс НЕ
    // добавляется — у текущих пользователей favorites/watch_history уже
    // ссылаются на старые id напрямую, смена схемы задним числом обнулила
    // бы им избранное и историю. false по умолчанию — у всех источников,
    // добавленных через обычный UI, коллизий с историческими id нет.
    val legacyIds: Boolean = false
)
