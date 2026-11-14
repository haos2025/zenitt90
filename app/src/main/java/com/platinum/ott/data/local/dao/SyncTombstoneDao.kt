package com.platinum.ott.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.platinum.ott.data.local.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones WHERE deletedAt > :since")
    suspend fun getSince(since: Long): List<SyncTombstoneEntity>

    @Query("SELECT * FROM sync_tombstones WHERE kind = :kind AND contentId = :contentId LIMIT 1")
    suspend fun get(kind: String, contentId: String): SyncTombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND contentId = :contentId")
    suspend fun remove(kind: String, contentId: String): Int

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :before")
    suspend fun purgeOlderThan(before: Long): Int
}
