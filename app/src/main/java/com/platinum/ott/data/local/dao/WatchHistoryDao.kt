package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>>
    @Query("SELECT * FROM watch_history WHERE contentId = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): WatchHistoryEntity?
    @Query("DELETE FROM watch_history WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String): Int
    @Query("DELETE FROM watch_history")
    suspend fun clearAll(): Int
    @Query("SELECT * FROM watch_history WHERE watchedAt > :since ORDER BY watchedAt DESC")
    suspend fun getSince(since: Long): List<WatchHistoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WatchHistoryEntity>)
}
