package com.platinum.ott.domain.usecase

import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.domain.model.Movie

class GetPlaylistCatalogUseCase(private val repository: PlaylistRepository) {
    suspend fun execute(forceRefresh: Boolean = false): List<Movie> = repository.getCatalog(forceRefresh)
}
