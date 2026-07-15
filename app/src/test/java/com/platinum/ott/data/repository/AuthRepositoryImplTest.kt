package com.platinum.ott.data.repository

import com.platinum.ott.core.AuthPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryImplTest {
    private lateinit var prefs: AuthPreferences
    private lateinit var client: OkHttpClient
    private lateinit var repo: AuthRepositoryImpl

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        client = OkHttpClient()
        repo = AuthRepositoryImpl(prefs, client)
    }

    @Test
    fun `isLoggedIn returns false when no credentials`() {
        every { prefs.isLoggedIn() } returns false
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun `logout clears prefs`() {
        repo.logout()
        verify { prefs.clear() }
    }
}
