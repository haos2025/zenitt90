package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

class FavoritesUseCase(private val dao: FavoritesDao) {
    fun getAllFavorites(): Flow<List<FavoriteEntity>> = dao.getAllFavorites()
    fun getByType(type: String): Flow<List<FavoriteEntity>> = dao.getFavoritesByType(type)
    fun getByFolder(folderId: Long): Flow<List<FavoriteEntity>> = dao.getFavoritesByFolder(folderId)
    fun getAllFolders(): Flow<List<FolderEntity>> = dao.getAllFolders()
    suspend fun isFavorite(contentId: String) = dao.isFavorite(contentId)
    suspend fun getByContentId(contentId: String) = dao.getFavoriteByContentId(contentId)
    suspend fun toggle(fav: FavoriteEntity) { if (dao.isFavorite(fav.contentId)) dao.deleteByContentId(fav.contentId) else dao.insertFavorite(fav) }
    suspend fun setAnime(contentId: String, isAnime: Boolean) = dao.setAnime(contentId, isAnime)
    suspend fun moveToFolder(contentId: String, folderId: Long?) = dao.moveToFolder(contentId, folderId)
    suspend fun createFolder(folder: FolderEntity) = dao.insertFolder(folder)
    // Решено с пользователем: удаление папки удаляет и её содержимое
    // (не переносит в "без папки") — см. deleteFolderWithContents в DAO.
    suspend fun deleteFolder(folder: FolderEntity) = dao.deleteFolderWithContents(folder)
}
