package com.platinum.ott.data.remote.dto

data class SyncResponseDto(val favorites: List<FavoriteDto> = emptyList(), val watchHistory: List<WatchHistoryDto> = emptyList(), val serverTimestamp: Long = 0)
data class SyncPushDto(val favorites: List<FavoriteDto> = emptyList(), val watchHistory: List<WatchHistoryDto> = emptyList(), val clientTimestamp: Long = 0)
data class FavoriteDto(val contentId: String, val contentType: String = "MOVIE", val title: String = "", val poster: String? = null, val updatedAt: Long = 0)
data class WatchHistoryDto(val contentId: String, val title: String = "", val poster: String? = null, val positionMs: Long = 0, val durationMs: Long = 0, val completed: Boolean = false, val updatedAt: Long = 0)
