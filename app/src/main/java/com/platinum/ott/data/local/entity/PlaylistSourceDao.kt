package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.PlaylistSourceEntity

@Dao
interface PlaylistSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: PlaylistSourceEntity)

    @Query("SELECT * FROM playlist_sources ORDER BY priority ASC")
    suspend fun getAll(): List<PlaylistSourceEntity>

    @Query("SELECT * FROM playlist_sources WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabled(): List<PlaylistSourceEntity>

    @Query("SELECT * FROM playlist_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaylistSourceEntity?

    @Query("SELECT COUNT(*) FROM playlist_sources")
    suspend fun getCount(): Int

    // Для присвоения priority новому источнику (кладём его в конец списка).
    @Query("SELECT MAX(priority) FROM playlist_sources")
    suspend fun getMaxPriority(): Int?

    @Query("DELETE FROM playlist_sources WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE playlist_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE playlist_sources SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: String, priority: Int)

    // Вызывается из PlaylistSourceRepository.refresh(sourceId) после каждой
    // попытки — и при успехе, и при ошибке (status = текст исключения),
    // чтобы карточка источника в UI всегда показывала актуальный результат
    // последней попытки, а не только последнего успеха.
    @Query("UPDATE playlist_sources SET lastRefreshedAt = :timestamp, lastRefreshStatus = :status WHERE id = :id")
    suspend fun updateRefreshResult(id: String, timestamp: Long, status: String)
}
