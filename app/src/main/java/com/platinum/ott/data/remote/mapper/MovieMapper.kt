package com.platinum.ott.data.remote.mapper

import com.platinum.ott.data.local.entity.MovieEntity
import com.platinum.ott.data.remote.dto.MovieDto
import com.platinum.ott.domain.model.Movie

object MovieMapper {
    fun MovieDto.toDomain() = Movie(id, year, title, poster ?: "", description ?: "", genre ?: "", duration ?: "", rating ?: 0.0, streamUrl ?: "")
    fun MovieDto.toEntity() = MovieEntity(id, title, year, poster, null, description, genre, duration, rating, streamUrl)
}
