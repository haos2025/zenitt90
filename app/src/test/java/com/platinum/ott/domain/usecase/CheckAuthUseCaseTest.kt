package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckAuthUseCaseTest {
    private lateinit var authRepo: AuthRepository
    private lateinit var useCase: CheckAuthUseCase

    @Before
    fun setup() {
        authRepo = mockk(relaxed = true)
        useCase = CheckAuthUseCase(authRepo)
    }

    @Test
    fun `returns true when repository reports logged in`() {
        every { authRepo.isLoggedIn() } returns true
        assertTrue(useCase.execute())
    }

    @Test
    fun `returns false when repository reports not logged in`() {
        // Гейт на HomeScreen (см. NEXT_STEPS.md) раньше зависел от этого
        // булева напрямую — регрессия здесь означала бы либо всех пускать
        // на home без плейлиста ошибочно, либо наоборот блокировать всех.
        every { authRepo.isLoggedIn() } returns false
        assertFalse(useCase.execute())
    }
}
