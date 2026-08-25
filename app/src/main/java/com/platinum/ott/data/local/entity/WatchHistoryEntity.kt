package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentId: String, val title: String = "",
    val poster: String? = null, val positionMs: Long = 0,
    val durationMs: Long = 0, val watchedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    // Раньше история не знала, что несколько записей относятся к одному
    // сериалу — каждая серия писала свою строку по contentId конкретного
    // эпизода. seriesId (тот же признак, что уже использует Movie/
    // SeriesEpisodesScreen) позволяет HistoryViewModel схлопнуть их в одну
    // строку "Продолжить [Название]" по самой свежей по watchedAt записи.
    // Nullable без DEFAULT — как и прошлые ADD COLUMN в этой таблице
    // (см. ZenithDatabase.MIGRATION_9_10), для обычных фильмов остаётся null.
    val seriesId: String? = null
)
