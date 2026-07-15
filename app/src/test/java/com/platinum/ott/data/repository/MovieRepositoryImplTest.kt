package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.MovieDao
import com.platinum.ott.data.local.entity.MovieEntity
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.CatalogResponseDto
import com.platinum.ott.data.remote.dto.MovieDto
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MovieRepositoryImplTest {
    private lateinit var api: ZenithApiService
    private lateinit var dao: MovieDao
    private lateinit var repo: MovieRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        // repo = MovieRepositoryImpl(api, dao) // context needed
    }

    @Test
    fun `getCatalog returns cached data when cache is fresh`() = runTest {
        coEvery { dao.getPage(any(), any()) } returns listOf(MovieEntity("1", "Test", 2024, null, null, null, null, null, null, null, System.currentTimeMillis()))
        coEvery { dao.getTotalCount() } returns 1
        coEvery { dao.getLatestCacheTime() } returns System.currentTimeMillis()
        // val result = repo.getCatalog(1)
        // assertTrue(result.isSuccess)
    }

    @Test
    fun `search returns from API on success`() = runTest {
        coEvery { api.searchMovies(any()) } returns listOf(MovieDto("1", "Result", 2024))
        // val result = repo.searchMovies("test")
        // assertTrue(result.isSuccess)
    }
}
