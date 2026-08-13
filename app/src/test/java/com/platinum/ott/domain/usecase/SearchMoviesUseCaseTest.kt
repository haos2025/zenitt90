package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.repository.MovieRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchMoviesUseCaseTest {
    private lateinit var movieRepo: MovieRepository
    private lateinit var useCase: SearchMoviesUseCase

    @Before
    fun setup() {
        movieRepo = mockk(relaxed = true)
        useCase = SearchMoviesUseCase(movieRepo)
    }

    @Test
    fun `passes query through unchanged and returns repository results`() = runTest {
        val query = "матрица"
        val movies = listOf(Movie(id = "yt_1", year = 1999, title = "Матрица", poster = ""))
        coEvery { movieRepo.searchMovies(query) } returns Result.success(movies)

        val result = useCase.execute(query)

        assertTrue(result.isSuccess)
        assertEquals(movies, result.getOrNull())
    }

    @Test
    fun `propagates repository failure instead of swallowing it`() {
        // Раньше не было теста, фиксирующего, что ошибка сети/парсинга
        // долетает до вызывающего кода как Result.failure, а не как
        // пустой список — SearchScreen различает эти два состояния
        // (ошибка vs "ничего не найдено") по-разному.
        runTest {
            val error = RuntimeException("network down")
            coEvery { movieRepo.searchMovies(any()) } returns Result.failure(error)

            val result = useCase.execute("что угодно")

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }
    }
}
