package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.PlaylistMovieEntity

@Dao
interface PlaylistMovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<PlaylistMovieEntity>)
    @Query("SELECT * FROM playlist_movies ORDER BY title ASC")
    suspend fun getAll(): List<PlaylistMovieEntity>
    @Query("SELECT * FROM playlist_movies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaylistMovieEntity?
    @Query("SELECT MAX(cachedAt) FROM playlist_movies")
    suspend fun getLatestCacheTime(): Long?
    @Query("DELETE FROM playlist_movies")
    suspend fun clearAll(): Int
}
