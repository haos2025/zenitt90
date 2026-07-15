package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String, val folderId: Long? = null,
    val contentType: String = "MOVIE", val title: String = "",
    val poster: String? = null, val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(), val sortOrder: Int = 0
)
