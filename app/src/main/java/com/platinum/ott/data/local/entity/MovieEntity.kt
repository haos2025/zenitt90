package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: String, val title: String, val year: Int,
    val poster: String?, val backdrop: String?, val description: String?,
    val genre: String?, val duration: String?, val rating: Double?,
    val streamUrl: String?, val cachedAt: Long = System.currentTimeMillis()
)
