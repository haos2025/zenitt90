package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(fav: FavoriteEntity)
    @Delete
    suspend fun deleteFavorite(fav: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String): Int
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE contentType = :type ORDER BY addedAt DESC")
    fun getFavoritesByType(type: String): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun getFavoritesByFolder(folderId: Long): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId)")
    suspend fun isFavorite(contentId: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    @Query("SELECT * FROM folders ORDER BY sortOrder")
    fun getAllFolders(): Flow<List<FolderEntity>>
    @Query("UPDATE favorites SET folderId = :folderId WHERE contentId = :contentId")
    suspend fun moveToFolder(contentId: String, folderId: Long?): Int
    @Query("UPDATE favorites SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllFavorites(favs: List<FavoriteEntity>)
    @Query("SELECT * FROM favorites WHERE contentId = :contentId LIMIT 1")
    suspend fun getFavoriteByContentId(contentId: String): FavoriteEntity?
    @Query("UPDATE favorites SET isAnime = :isAnime WHERE contentId = :contentId")
    suspend fun setAnime(contentId: String, isAnime: Boolean): Int
    @Query("DELETE FROM favorites WHERE folderId = :folderId")
    suspend fun deleteFavoritesInFolder(folderId: Long): Int
    // Решено при обсуждении задачи "папки в избранном": при удалении папки
    // удалять и её содержимое (не переносить в "без папки"). @Transaction
    // на default-методе интерфейса — стандартный паттерн Room для нескольких
    // запросов, которые должны либо оба применяться, либо ни один.
    @Transaction
    suspend fun deleteFolderWithContents(folder: FolderEntity) {
        deleteFavoritesInFolder(folder.id)
        deleteFolder(folder)
    }
}
