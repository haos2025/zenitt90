package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Отдельная таблица для контента из M3U/Xtream-плейлиста пользователя.
 * Специально НЕ переиспользует таблицу movies (MovieEntity), хотя формат
 * почти идентичен: movies — это 10-минутный кэш поверх Zenith backend
 * (MovieRepositoryImpl сверяет getLatestCacheTime(), чтобы решить, идти ли
 * за свежими данными в ZenithApiService), и подмешивание туда чужих строк
 * рискует тихо сломать эту проверку (например, если бы TTL считался по
 * последней вставленной строке — свежий плейлист-рефреш маскировал бы
 * устаревший backend-кэш). streamUrl здесь ВСЕГДА заполнен — в отличие от
 * movies, где ссылка на поток резолвится отдельным сетевым запросом
 * (GetPlayableUrlUseCase → /stream/{id}), у M3U/Xtream прямая ссылка уже
 * известна сразу из плейлиста/API, второй поход в сеть не нужен.
 */
@Entity(tableName = "playlist_movies")
data class PlaylistMovieEntity(
    @PrimaryKey val id: String,
    val title: String,
    val year: Int,
    val poster: String?,
    val genre: String?,
    val streamUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)
