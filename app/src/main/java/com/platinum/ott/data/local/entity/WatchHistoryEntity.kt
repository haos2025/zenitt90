package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentId: String, val title: String = "",
    val poster: String? = null, val positionMs: Long = 0,
    val durationMs: Long = 0, val watchedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)
