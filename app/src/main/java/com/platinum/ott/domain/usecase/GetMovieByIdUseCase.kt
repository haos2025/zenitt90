package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.MovieRepository

class GetMovieByIdUseCase(private val repo: MovieRepository) {
    suspend fun execute(id: String) = repo.getMovieById(id)
}
