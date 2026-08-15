package com.platinum.ott.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.FolderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    val favorites = sessionGraph.favoritesUseCase.getAllFavorites()
    val folders = sessionGraph.favoritesUseCase.getAllFolders()

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { sessionGraph.favoritesUseCase.createFolder(FolderEntity(name = name.trim())) }
    }

    // Решено с пользователем: удаление папки удаляет и содержимое папки
    // целиком (не переносит фильмы/сериалы в "без папки").
    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch { sessionGraph.favoritesUseCase.deleteFolder(folder) }
    }

    fun moveToFolder(contentId: String, folderId: Long?) {
        viewModelScope.launch { sessionGraph.favoritesUseCase.moveToFolder(contentId, folderId) }
    }

    fun setAnime(contentId: String, isAnime: Boolean) {
        viewModelScope.launch { sessionGraph.favoritesUseCase.setAnime(contentId, isAnime) }
    }
}
