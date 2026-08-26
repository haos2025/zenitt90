package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.MetadataEntity

@Dao
interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: MetadataEntity)
    @Query("SELECT * FROM metadata WHERE contentId = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): MetadataEntity?
    @Query("DELETE FROM metadata")
    suspend fun clearAll(): Int
    // Добавлено для экрана управления кэшем (PROMPT_CACHE_MANAGEMENT.md) —
    // тот же приём, что и в PlaylistMovieDao.getCount(): количество записей
    // вместо точного размера в байтах, который недоступен без dbstat.
    @Query("SELECT COUNT(*) FROM metadata")
    suspend fun getCount(): Int
}
