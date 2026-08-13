package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.SeriesScheduleDao
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import com.platinum.ott.domain.model.NextEpisode
import com.platinum.ott.domain.repository.TmdbRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SeriesTrackerUseCaseTest {
    private lateinit var dao: SeriesScheduleDao
    private lateinit var tmdb: TmdbRepository
    private lateinit var useCase: SeriesTrackerUseCase

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        tmdb = mockk(relaxed = true)
        useCase = SeriesTrackerUseCase(dao, tmdb)
    }

    @Test
    fun `returns null and does not upsert when TMDB has no next episode`() = runTest {
        coEvery { tmdb.getNextEpisode("Сериал") } returns null

        val result = useCase.updateSchedule("s1", "Сериал")

        assertNull(result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `returns null and skips upsert when the next episode date is unchanged`() = runTest {
        // Это и есть починенный баг из ROADMAP.md: раньше был placeholder,
        // ничего не делавший — вернуть сюда "всегда обновлять" было бы
        // регрессией, SeriesUpdateWorker расценил бы каждый опрос как
        // "новая серия" и слал бы уведомление постоянно.
        val next = NextEpisode(airDateEpochMs = 1000L, seasonNum = 2, episodeNum = 3, episodeName = "Финал")
        val existing = SeriesScheduleEntity(
            seriesId = "s1", seriesName = "Сериал",
            nextEpisodeDate = 1000L, seasonNum = 2, episodeNum = 3, episodeName = "Финал"
        )
        coEvery { tmdb.getNextEpisode("Сериал") } returns next
        coEvery { dao.getBySeriesId("s1") } returns existing

        val result = useCase.updateSchedule("s1", "Сериал")

        assertNull(result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `upserts and returns the entity when the next episode date actually changed`() = runTest {
        val next = NextEpisode(airDateEpochMs = 2000L, seasonNum = 2, episodeNum = 4, episodeName = "Новая серия")
        val existing = SeriesScheduleEntity(
            seriesId = "s1", seriesName = "Сериал",
            nextEpisodeDate = 1000L, seasonNum = 2, episodeNum = 3, episodeName = "Финал"
        )
        coEvery { tmdb.getNextEpisode("Сериал") } returns next
        coEvery { dao.getBySeriesId("s1") } returns existing

        val result = useCase.updateSchedule("s1", "Сериал")

        assertEquals(2000L, result?.nextEpisodeDate)
        assertEquals(4, result?.episodeNum)
        coVerify(exactly = 1) { dao.upsert(result!!) }
    }

    @Test
    fun `upserts when there is no existing schedule row yet`() = runTest {
        val next = NextEpisode(airDateEpochMs = 500L, seasonNum = 1, episodeNum = 1, episodeName = "Пилот")
        coEvery { tmdb.getNextEpisode("Новый сериал") } returns next
        coEvery { dao.getBySeriesId("s2") } returns null

        val result = useCase.updateSchedule("s2", "Новый сериал")

        assertEquals("s2", result?.seriesId)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }
}
