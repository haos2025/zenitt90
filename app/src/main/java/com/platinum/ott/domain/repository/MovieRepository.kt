package com.platinum.ott.domain.repository

import com.platinum.ott.domain.model.CatalogPage
import com.platinum.ott.domain.model.Movie

interface MovieRepository {
    suspend fun getCatalog(page: Int = 1, genre: String? = null): Result<CatalogPage>
    suspend fun getMovieById(id: String): Result<Movie>
    suspend fun searchMovies(query: String): Result<List<Movie>>
}
