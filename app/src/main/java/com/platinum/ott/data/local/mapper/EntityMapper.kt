package com.platinum.ott.data.local.mapper

import com.platinum.ott.data.local.entity.*
import com.platinum.ott.domain.model.*

object EntityMapper {
    fun MovieEntity.toDomain() = Movie(id, year, title, poster ?: "", description ?: "", genre ?: "", duration ?: "", rating ?: 0.0, streamUrl ?: "")
    fun Movie.toEntity() = MovieEntity(id, title, year, poster, null, description, genre, duration, rating, streamUrl)
    fun FavoriteEntity.toDomain() = Favorite(contentId, folderId, contentType, title, poster, addedAt)
    fun Favorite.toEntity() = FavoriteEntity(contentId = contentId, folderId = folderId, contentType = contentType, title = title, poster = poster, addedAt = addedAt)
    fun WatchHistoryEntity.toDomain() = WatchHistory(contentId, title, poster, positionMs, durationMs, watchedAt, completed)
}
