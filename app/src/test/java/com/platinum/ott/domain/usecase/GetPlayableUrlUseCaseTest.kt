package com.platinum.ott.domain.usecase

import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.data.local.entity.PluginEntity
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.StreamVariantDto
import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.data.repository.PlaylistStreamInfo
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetPlayableUrlUseCaseTest {
    private lateinit var scriptProvider: ScriptProvider
    private lateinit var api: ZenithApiService
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var pluginManager: PluginManager
    private lateinit var getMovie: GetMovieByIdUseCase
    private lateinit var authRepo: AuthRepository
    private lateinit var useCase: GetPlayableUrlUseCase

    @Before
    fun setup() {
        scriptProvider = mockk(relaxed = true)
        api = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        pluginManager = mockk(relaxed = true)
        getMovie = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        useCase = GetPlayableUrlUseCase(scriptProvider, api, playlistRepository, pluginManager, getMovie, authRepo)

        // По умолчанию — ни один плагин не включён, гонка вырождается в
        // "только backend". Отдельные тесты переопределяют это явно.
        every { pluginManager.getEnabledPlugins() } returns flowOf(emptyList())
    }

    // --- m3u_ / xt_ — собственный плейлист, без второго сетевого запроса ---

    @Test
    fun `m3u prefix resolves directly from playlist repository without touching backend`() = runTest {
        val info = PlaylistStreamInfo(url = "http://example.com/stream.m3u8", headers = mapOf("User-Agent" to "VLC"))
        coEvery { playlistRepository.getStreamInfo("m3u_42") } returns info

        val result = useCase.execute("m3u_42")

        assertEquals(1, result.size)
        assertEquals("Мой плейлист", result[0].source)
        assertEquals(info.url, result[0].url)
        assertEquals(info.headers, result[0].headers)
    }

    @Test
    fun `xt prefix returns empty list when playlist repository has no entry for the id`() = runTest {
        coEvery { playlistRepository.getStreamInfo("xt_missing") } returns null

        val result = useCase.execute("xt_missing")

        assertTrue(result.isEmpty())
    }

    // --- yt_ / ia_ — гибридная модель: backend и плагины параллельно ---

    @Test
    fun `yt prefix returns backend variants when no plugins are enabled`() = runTest {
        coEvery { api.getStreamVariants("yt_1") } returns listOf(StreamVariantDto("1080p", "http://a"))

        val result = useCase.execute("yt_1")

        assertEquals(1, result.size)
        assertEquals("Zenith", result[0].source)
        assertEquals("1080p", result[0].quality)
    }

    @Test
    fun `ia prefix combines backend and plugin results rather than one replacing the other`() = runTest {
        val movie = Movie(id = "ia_1", year = 2020, title = "Фильм", poster = "")
        coEvery { api.getStreamVariants("ia_1") } returns listOf(StreamVariantDto("720p", "http://backend"))
        coEvery { getMovie.execute("ia_1") } returns Result.success(movie)
        val plugin = PluginEntity(id = "p1", name = "MyPlugin", version = "1.0")
        every { pluginManager.getEnabledPlugins() } returns flowOf(listOf(plugin))
        coEvery { pluginManager.callPluginFunction("p1", "findStream", "Фильм", "2020") } returns
            """[{"quality":"480p","url":"http://plugin"}]"""

        val result = useCase.execute("ia_1")

        assertEquals(2, result.size)
        assertTrue(result.any { it.source == "Zenith" && it.quality == "720p" })
        assertTrue(result.any { it.source == "MyPlugin" && it.quality == "480p" })
    }

    @Test
    fun `backend failure does not prevent plugin results from coming through`() = runTest {
        // Ядро "гибридной модели" (см. комментарий в самом use case) —
        // сбой ОДНОГО источника не должен ронять весь результат.
        val movie = Movie(id = "yt_1", year = 2020, title = "Фильм", poster = "")
        coEvery { api.getStreamVariants("yt_1") } throws RuntimeException("backend 500")
        coEvery { getMovie.execute("yt_1") } returns Result.success(movie)
        val plugin = PluginEntity(id = "p1", name = "MyPlugin", version = "1.0")
        every { pluginManager.getEnabledPlugins() } returns flowOf(listOf(plugin))
        coEvery { pluginManager.callPluginFunction("p1", "findStream", "Фильм", "2020") } returns
            """[{"quality":"480p","url":"http://plugin"}]"""

        val result = useCase.execute("yt_1")

        assertEquals(1, result.size)
        assertEquals("MyPlugin", result[0].source)
    }

    @Test
    fun `plugin failure does not prevent backend result from coming through`() = runTest {
        val movie = Movie(id = "yt_1", year = 2020, title = "Фильм", poster = "")
        coEvery { api.getStreamVariants("yt_1") } returns listOf(StreamVariantDto("1080p", "http://a"))
        coEvery { getMovie.execute("yt_1") } returns Result.success(movie)
        val plugin = PluginEntity(id = "p1", name = "MyPlugin", version = "1.0")
        every { pluginManager.getEnabledPlugins() } returns flowOf(listOf(plugin))
        // Плагин не экспортирует findStream — PluginManager по контракту
        // возвращает null, это не ошибка, просто плагин не участвует.
        coEvery { pluginManager.callPluginFunction("p1", "findStream", "Фильм", "2020") } returns null

        val result = useCase.execute("yt_1")

        assertEquals(1, result.size)
        assertEquals("Zenith", result[0].source)
    }

    @Test
    fun `no plugins race at all when the movie itself cannot be resolved`() = runTest {
        coEvery { api.getStreamVariants("yt_1") } returns listOf(StreamVariantDto("1080p", "http://a"))
        coEvery { getMovie.execute("yt_1") } returns Result.failure(RuntimeException("not found"))

        val result = useCase.execute("yt_1")

        assertEquals(1, result.size)
        assertEquals("Zenith", result[0].source)
    }

    // --- любой другой префикс — ScriptProvider ("parser"-скрипт) ---

    @Test
    fun `unknown prefix resolves through the ScriptProvider parser script`() = runTest {
        every {
            scriptProvider.evaluateScript("player_parser", "parseMovie", "custom_id")
        } returns """[{"quality":"SD","url":"http://parsed"}]"""

        val result = useCase.execute("custom_id")

        assertEquals(1, result.size)
        assertEquals("Плагин", result[0].source)
        assertEquals("SD", result[0].quality)
    }

    @Test
    fun `unknown prefix returns empty list when the parser script yields nothing`() = runTest {
        every { scriptProvider.evaluateScript(any(), any(), any()) } returns null

        val result = useCase.execute("custom_id")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown prefix returns empty list instead of throwing on malformed parser output`() = runTest {
        every { scriptProvider.evaluateScript(any(), any(), any()) } returns "not valid json"

        val result = useCase.execute("custom_id")

        assertTrue(result.isEmpty())
    }
}
