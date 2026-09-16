package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Конкретная ссылка от конкретного источника (PlaylistSourceEntity) на
 * конкретный канонический канал (ChannelEntity) — многие-ко-многим между
 * ними реализовано через эту промежуточную таблицу, не через прямые FK на
 * ChannelEntity.
 *
 * lastCheckedAt/lastCheckStatus — под health-check (отдельная будущая
 * подзадача этой темы): пишутся туда, читаются при воспроизведении для
 * выбора живого стрима среди нескольких (см. GetPlayableUrlUseCase —
 * подзадача "fallback при воспроизведении").
 *
 * priority отдельно от PlaylistSourceEntity.priority намеренно: источник
 * в среднем надёжен, но конкретно этот канал у него сейчас может быть
 * плохим (устаревший id в панели, битый транскодер и т.п.) — общая
 * надёжность источника и надёжность одного канала у него — разные вещи.
 */
@Entity(
    tableName = "channel_streams",
    indices = [Index(value = ["channelId"]), Index(value = ["sourceId"])]
)
data class ChannelStreamEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val sourceId: String,
    val streamUrl: String,
    // Оригинальное название из #EXTINF/Xtream-ответа ЭТОГО источника —
    // хранится отдельно от ChannelEntity.canonicalName для отладки
    // сопоставления (когда пользователь спрашивает "почему эти два канала
    // объединились/не объединились", здесь видно, что реально пришло от
    // каждого источника).
    val rawTitle: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    // null = проверка ни разу не запускалась с момента создания записи.
    val lastCheckedAt: Long? = null,
    // "alive" | "dead" | "unknown"
    val lastCheckStatus: String = "unknown",
    // Тот же принцип "изоляции", что и на бэкенде (plugins/health.py,
    // call_with_isolation) — реализован на Kotlin-стороне (PROMPT_IPTV_FOUNDATION.md,
    // health-check-решение сессии: дублировать паттерн локально). Сбрасывается
    // в 0 при первой же успешной проверке. Используется ChannelHealthChecker
    // для экспоненциального бэкоффа интервала следующей проверки — стрим,
    // который уже несколько раз подряд не ответил, не долбится на каждом
    // цикле наравне со свежедобавленным/живым.
    val consecutiveFailures: Int = 0,
    // Приоритет ЭТОГО стрима для ЭТОГО канала — не путать с приоритетом
    // источника в целом (PlaylistSourceEntity.priority). Меньше — выше,
    // тот же порядок сравнения, что и у источников.
    val priority: Int = 0
)
