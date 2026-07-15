package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.MovieRepository

class SearchMoviesUseCase(private val repo: MovieRepository) {
    suspend fun execute(query: String) = repo.searchMovies(query)
}
