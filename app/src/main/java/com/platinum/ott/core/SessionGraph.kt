package com.platinum.ott.core

import android.content.Context
import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.core.plugin.PluginApi
import com.platinum.ott.core.plugin.PluginManager
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.ZenithDatabase
import com.platinum.ott.data.remote.RetrofitFactory
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import com.platinum.ott.data.remote.tmdb.TmdbInterceptor
import com.platinum.ott.data.repository.*
import com.platinum.ott.domain.repository.*
import com.platinum.ott.domain.usecase.*
import com.platinum.ott.sync.SyncRepository
import com.platinum.ott.sync.SyncRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переезд ServiceLocator → Hilt (см. NEXT_STEPS.md / REFACTOR_PROMPT.md).
 * database/preferences сюда больше НЕ создаются сами — их даёт Hilt
 * (DatabaseModule/PreferencesModule), потому что они не зависят от логина
 * и не участвуют в reinitWithAuth(). Всё остальное ниже — дословно то, что
 * раньше делал ServiceLocator.initAuth()/reinitWithAuth(), без изменений
 * поведения.
 *
 * ВАЖНО: этот класс пока НИКЕМ не используется — ServiceLocator.kt
 * продолжает обслуживать все текущие обращения сам по себе. Подключение
 * (инжект в ZenithApplication/ViewModel'и и постепенный перенос вызовов)
 * — предмет следующих шагов, по одному месту за раз.
 */
@Singleton
class SessionGraph @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: ZenithDatabase,
    private val authPreferences: AuthPreferences,
    private val networkPreferences: NetworkPreferences,
    private val notificationPreferences: NotificationPreferences
) {
    lateinit var authRepository: AuthRepository; private set
    lateinit var movieRepository: MovieRepository; private set
    lateinit var playlistRepository: PlaylistRepository; private set
    lateinit var tmdbApi: TmdbApiService; private set
    lateinit var tmdbRepository: TmdbRepository; private set
    lateinit var syncRepository: SyncRepository; private set
    lateinit var checkAuthUseCase: CheckAuthUseCase; private set
    lateinit var loginM3UUseCase: LoginM3UUseCase; private set
    lateinit var loginXtreamUseCase: LoginXtreamUseCase; private set
    lateinit var logoutUseCase: LogoutUseCase; private set
    lateinit var getCatalogUseCase: GetCatalogUseCase; private set
    lateinit var getPlaylistCatalogUseCase: GetPlaylistCatalogUseCase; private set
    lateinit var getMovieByIdUseCase: GetMovieByIdUseCase; private set
    lateinit var getPlayableUrlUseCase: GetPlayableUrlUseCase; private set
    lateinit var searchMoviesUseCase: SearchMoviesUseCase; private set
    lateinit var cacheManagementUseCase: CacheManagementUseCase; private set
    lateinit var otaUpdateUseCase: OtaUpdateUseCase; private set
    lateinit var favoritesUseCase: FavoritesUseCase; private set
    lateinit var watchHistoryUseCase: WatchHistoryUseCase; private set
    lateinit var syncUseCase: SyncUseCase; private set
    lateinit var seriesTrackerUseCase: SeriesTrackerUseCase; private set
    lateinit var pluginManager: PluginManager; private set
    lateinit var pluginRepository: PluginRepository; private set

    val scriptProvider: ScriptProvider by lazy { ScriptProvider(appContext) }
    val pluginApi: PluginApi by lazy { PluginApi(appContext) }

    // Тот же смысл, что и appScope в ServiceLocator: единственный на
    // приложение scope для fire-and-forget задачи загрузки плагинов при
    // (пере)инициализации графа.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        initAuth()
    }

    fun initAuth() {
        val timeoutSeconds = networkPreferences.getTimeoutSeconds().toLong()
        val okHttpClient = RetrofitFactory.createOkHttpClient(authPreferences, timeoutSeconds = timeoutSeconds)
        val api = RetrofitFactory.createApi(okHttpClient)
        authRepository = AuthRepositoryImpl(authPreferences, okHttpClient)
        playlistRepository = PlaylistRepository(authPreferences, database.playlistMovieDao(), okHttpClient)
        movieRepository = MovieRepositoryImpl(api, database.movieDao(), playlistRepository)
        val tmdbClient = RetrofitFactory.createOkHttpClient(authPreferences, TmdbInterceptor(), timeoutSeconds = timeoutSeconds)
        tmdbApi = RetrofitFactory.createTmdbApi(tmdbClient)
        tmdbRepository = TmdbRepositoryImpl(tmdbApi, database.metadataDao())
        syncRepository = SyncRepositoryImpl(api, database.favoritesDao(), database.watchHistoryDao(), authPreferences)
        checkAuthUseCase = CheckAuthUseCase(authRepository)
        loginM3UUseCase = LoginM3UUseCase(authRepository)
        loginXtreamUseCase = LoginXtreamUseCase(authRepository)
        logoutUseCase = LogoutUseCase(authRepository)
        getCatalogUseCase = GetCatalogUseCase(movieRepository)
        getPlaylistCatalogUseCase = GetPlaylistCatalogUseCase(playlistRepository)
        getMovieByIdUseCase = GetMovieByIdUseCase(movieRepository)
        pluginManager = PluginManager(appContext, database.pluginDao(), pluginApi)
        pluginRepository = PluginRepository(database.pluginDao(), pluginManager, pluginApi)
        appScope.launch { pluginManager.loadAllEnabled() }
        getPlayableUrlUseCase = GetPlayableUrlUseCase(scriptProvider, api, playlistRepository, pluginManager, getMovieByIdUseCase, authRepository)
        searchMoviesUseCase = SearchMoviesUseCase(movieRepository)
        cacheManagementUseCase = CacheManagementUseCase(appContext, database.movieDao(), database.playlistMovieDao(), database.metadataDao())
        otaUpdateUseCase = OtaUpdateUseCase(scriptProvider, api)
        favoritesUseCase = FavoritesUseCase(database.favoritesDao())
        watchHistoryUseCase = WatchHistoryUseCase(database.watchHistoryDao())
        syncUseCase = SyncUseCase(syncRepository)
        seriesTrackerUseCase = SeriesTrackerUseCase(database.seriesScheduleDao(), tmdbRepository)
    }

    fun reinitWithAuth() {
        if (::pluginManager.isInitialized) pluginManager.destroy()
        initAuth()
    }
}
