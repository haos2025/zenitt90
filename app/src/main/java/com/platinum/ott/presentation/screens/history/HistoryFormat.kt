package com.platinum.ott.presentation.screens.history

import com.platinum.ott.data.local.entity.WatchHistoryEntity

/** Подпись записи истории: «Просмотрено» или «Осталось N мин · P%». Общая для ТВ и телефона. */
fun historySubtitle(entry: WatchHistoryEntity): String {
    if (entry.completed) return "Просмотрено"
    if (entry.durationMs <= 0) return "Продолжить просмотр"
    val percent = (entry.positionMs * 100 / entry.durationMs).toInt().coerceIn(0, 100)
    val leftMin = ((entry.durationMs - entry.positionMs) / 60_000).coerceAtLeast(0)
    return if (leftMin >= 1) "Осталось $leftMin мин · $percent%" else "$percent%"
}
