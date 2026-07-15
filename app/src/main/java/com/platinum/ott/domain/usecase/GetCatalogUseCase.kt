package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.MovieRepository

class GetCatalogUseCase(private val repo: MovieRepository) {
    suspend fun execute(page: Int = 1, genre: String? = null) = repo.getCatalog(page, genre)
}
