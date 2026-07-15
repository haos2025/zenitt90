package com.platinum.ott.domain.model

data class WatchHistory(val contentId: String, val title: String = "", val poster: String? = null,
    val positionMs: Long = 0, val durationMs: Long = 0, val watchedAt: Long = System.currentTimeMillis(), val completed: Boolean = false)
