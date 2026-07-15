package com.platinum.ott.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import com.platinum.ott.core.ServiceLocator

class FavoritesViewModel : ViewModel() {
    val favorites = ServiceLocator.favoritesUseCase.getAllFavorites()
    val folders = ServiceLocator.favoritesUseCase.getAllFolders()
}
