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
    // Раньше добавление в избранное всегда шло через toggle() — с
    // PROMPT_FAVORITES_REDESIGN.md, п.2 точки входа (Detail/SeriesEpisodes)
    // сначала показывают выбор папки и только потом вставляют запись, а
    // снятие с избранного по-прежнему происходит сразу без диалога — двум
    // разным потокам UI нужны раздельные методы, а не один toggle().
    suspend fun add(fav: FavoriteEntity) = dao.insertFavorite(fav)
    suspend fun remove(contentId: String) = dao.deleteByContentId(contentId)
    suspend fun setAnime(contentId: String, isAnime: Boolean) = dao.setAnime(contentId, isAnime)
    suspend fun moveToFolder(contentId: String, folderId: Long?) = dao.moveToFolder(contentId, folderId)
    suspend fun createFolder(folder: FolderEntity) = dao.insertFolder(folder)
    // Решено с пользователем: удаление папки удаляет и её содержимое
    // (не переносит в "без папки") — см. deleteFolderWithContents в DAO.
    suspend fun deleteFolder(folder: FolderEntity) = dao.deleteFolderWithContents(folder)
}
