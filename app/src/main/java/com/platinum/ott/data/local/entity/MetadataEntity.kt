package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val contentId: String, val tmdbId: Int? = null,
    val posterPath: String? = null, val backdropPath: String? = null,
    val overview: String? = null, val voteAverage: Double? = null,
    val genres: String? = null, val trailerUrl: String? = null,
    val cast: String? = null, val cachedAt: Long = System.currentTimeMillis()
)
