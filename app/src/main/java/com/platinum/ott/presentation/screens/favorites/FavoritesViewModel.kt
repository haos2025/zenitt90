package com.platinum.ott.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.FolderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    val favorites = sessionGraph.favoritesUseCase.getAllFavorites()
    val folders = sessionGraph.favoritesUseCase.getAllFolders()

    fun createFolder(name: String, color: String) {
        if (name.isBlank()) return
        viewModelScope.launch { sessionGraph.favoritesUseCase.createFolder(FolderEntity(name = name.trim(), color = color)) }
    }

    fun setFolderColor(folder: FolderEntity, color: String) {
        viewModelScope.launch { sessionGraph.favoritesUseCase.setFolderColor(folder.id, color) }
    }

    /**
     * Меняет местами элемент и его соседа по ТЕКУЩЕМУ отображаемому списку
     * (с учётом фильтров) и перенумеровывает весь список избранного — так
     * порядок остаётся целостным и для скрытых фильтром элементов.
     */
    fun moveFavorite(contentId: String, neighborContentId: String) {
        viewModelScope.launch {
            val list = sessionGraph.favoritesUseCase.getAllFavorites().first().toMutableList()
            val a = list.indexOfFirst { it.contentId == contentId }
            val b = list.indexOfFirst { it.contentId == neighborContentId }
            if (a < 0 || b < 0) return@launch
            java.util.Collections.swap(list, a, b)
            sessionGraph.favoritesUseCase.reorder(list.map { it.id })
        }
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

    // Единое меню на карточке в FavoritesScreen/PhoneFavoritesScreen
    // (PROMPT_FAVORITES_REDESIGN.md, п.3) — "Убрать из избранного", без
    // диалога подтверждения (симметрично с тем, как снятие с избранного уже
    // устроено на DetailScreen/SeriesEpisodesScreen).
    fun removeFavorite(contentId: String) {
        viewModelScope.launch { sessionGraph.favoritesUseCase.remove(contentId) }
    }
}
