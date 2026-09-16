package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.ChannelEntity

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isSubscribed = 1 ORDER BY sortOrder ASC")
    suspend fun getSubscribed(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    // Для автосопоставления по tvg-id при обработке нового/обновлённого
    // источника — см. подзадачу "Сопоставление".
    @Query("SELECT * FROM channels WHERE tvgId = :tvgId LIMIT 1")
    suspend fun getByTvgId(tvgId: String): ChannelEntity?

    @Query("UPDATE channels SET isSubscribed = :isSubscribed WHERE id = :id")
    suspend fun setSubscribed(id: String, isSubscribed: Boolean)

    @Query("UPDATE channels SET canonicalName = :name, regionHint = :regionHint WHERE id = :id")
    suspend fun rename(id: String, name: String, regionHint: String?)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: String)
}
