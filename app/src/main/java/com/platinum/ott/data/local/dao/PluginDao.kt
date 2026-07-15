package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plugin: PluginEntity)

    @Query("SELECT * FROM plugins ORDER BY name ASC")
    fun getAll(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE isEnabled = 1 ORDER BY priority ASC")
    fun getEnabled(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE pluginType = :type AND isEnabled = 1 ORDER BY priority ASC")
    fun getEnabledByType(type: String): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PluginEntity?

    @Query("SELECT * FROM plugins WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<PluginEntity?>

    @Query("UPDATE plugins SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean): Int

    @Query("UPDATE plugins SET installedVersion = :version, updatedAt = :now WHERE id = :id")
    suspend fun updateVersion(id: String, version: String, now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM plugins")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM plugins WHERE isEnabled = 1")
    suspend fun getEnabledCount(): Int
}
