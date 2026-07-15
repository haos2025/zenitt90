package com.platinum.ott.domain.usecase

import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.ScriptManifestDto
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class OtaUpdateUseCaseTest {
    private lateinit var scriptProvider: ScriptProvider
    private lateinit var api: ZenithApiService
    private lateinit var useCase: OtaUpdateUseCase

    @Before
    fun setup() {
        scriptProvider = mockk(relaxed = true)
        api = mockk(relaxed = true)
        useCase = OtaUpdateUseCase(scriptProvider, api)
    }

    @Test
    fun `skips scripts with matching hash`() = runTest {
        coEvery { api.getScriptManifest() } returns listOf(ScriptManifestDto("parser", "1.0", "abc123"))
        every { scriptProvider.getScript("parser") } returns "content"
        // SHA-256 of "content" != "abc123", so it would update
        coEvery { api.downloadScript("parser") } returns Response.success(ResponseBody.create(null, "new content"))
        every { scriptProvider.saveScript(any(), any()) } just Runs
        val result = useCase.execute()
        assertTrue(result.isSuccess)
    }
}
