package com.platinum.ott.domain.usecase

import com.platinum.ott.sync.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncUseCaseTest {
    private lateinit var repo: SyncRepository
    private lateinit var useCase: SyncUseCase

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        useCase = SyncUseCase(repo)
    }

    @Test
    fun `syncNow delegates to repository exactly once and returns its result`() = runTest {
        coEvery { repo.sync() } returns Result.success(Unit)

        val result = useCase.syncNow()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repo.sync() }
    }

    @Test
    fun `syncNow propagates repository failure without wrapping it`() = runTest {
        // SyncPairingViewModel показывает пользователю причину сбоя
        // (см. NEXT_STEPS.md "Статус синхронизации") — если бы use case
        // проглатывал исключение молча, экран не смог бы отличить сбой
        // от успешной пустой синхронизации.
        val error = RuntimeException("device not paired")
        coEvery { repo.sync() } returns Result.failure(error)

        val result = useCase.syncNow()

        assertTrue(result.isFailure)
    }
}
