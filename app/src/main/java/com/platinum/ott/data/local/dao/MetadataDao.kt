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
}
