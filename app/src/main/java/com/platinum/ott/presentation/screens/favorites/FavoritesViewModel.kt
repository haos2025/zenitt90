package com.platinum.ott.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import com.platinum.ott.core.SessionGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    val favorites = sessionGraph.favoritesUseCase.getAllFavorites()
    val folders = sessionGraph.favoritesUseCase.getAllFolders()
}
