package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.MovieEntity

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: MovieEntity)
    @Query("SELECT * FROM movies ORDER BY year DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<MovieEntity>
    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getTotalCount(): Int
    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%' ORDER BY year DESC LIMIT 50")
    suspend fun search(query: String): List<MovieEntity>
    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MovieEntity?
    @Query("SELECT MAX(cachedAt) FROM movies")
    suspend fun getLatestCacheTime(): Long?
    @Query("DELETE FROM movies")
    suspend fun clearAll(): Int
}
