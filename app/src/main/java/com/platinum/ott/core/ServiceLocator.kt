package com.platinum.ott.core

import android.content.Context
import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.data.local.ZenithDatabase
import com.platinum.ott.data.remote.RetrofitFactory
import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.tmdb.TmdbApiService
import com.platinum.ott.data.remote.tmdb.TmdbInterceptor
import com.platinum.ott.data.repository.*
import com.platinum.ott.domain.repository.*
import com.platinum.ott.domain.usecase.*
import com.platinum.ott.sync.SyncRepository
import com.platinum.ott.sync.SyncRepositoryImpl

object ServiceLocator {
    private lateinit var appContext: Context
    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        interfacePreferences = InterfacePreferences(appContext)
        darkThemeFlow.value = interfacePreferences.isDarkTheme
        initAuth()
    }

    // --- Interface (тема) — независимо от авторизации, нужно ещё на
    // экране логина, поэтому инициализируется прямо в init(), не в initAuth() ---
    lateinit var interfacePreferences: InterfacePreferences; private set
    val darkThemeFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
    fun setDarkTheme(enabled: Boolean) {
        interfacePreferences.isDarkTheme = enabled
        darkThemeFlow.value = enabled
    }

    // --- Database ---
    val database: ZenithDatabase by lazy { ZenithDatabase.getInstance(appContext) }

    // --- Auth ---
    val authPreferences: AuthPreferences by lazy { AuthPreferences(appContext) }
    lateinit var authRepository: AuthRepository; private set
    lateinit var movieRepository: MovieRepository; private set
    lateinit var playlistRepository: com.platinum.ott.data.repository.PlaylistRepository; private set
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
    lateinit var clearCacheUseCase: ClearCacheUseCase; private set
    lateinit var otaUpdateUseCase: OtaUpdateUseCase; private set
    lateinit var favoritesUseCase: FavoritesUseCase; private set
    lateinit var watchHistoryUseCase: WatchHistoryUseCase; private set
    lateinit var syncUseCase: SyncUseCase; private set
    lateinit var seriesTrackerUseCase: SeriesTrackerUseCase; private set
    lateinit var pluginManager: com.platinum.ott.core.plugin.PluginManager; private set
    lateinit var pluginRepository: com.platinum.ott.core.plugin.PluginRepository; private set

    val scriptProvider: ScriptProvider by lazy { ScriptProvider(appContext) }
    val pluginApi: com.platinum.ott.core.plugin.PluginApi by lazy { com.platinum.ott.core.plugin.PluginApi(appContext) }

    fun initAuth() {
        val okHttpClient = RetrofitFactory.createOkHttpClient(authPreferences)
        val api = RetrofitFactory.createApi(okHttpClient)
        authRepository = AuthRepositoryImpl(authPreferences, okHttpClient)
        movieRepository = MovieRepositoryImpl(api, database.movieDao())
        // Раньше M3U/Xtream-логин был калиткой без последствий — учётные
        // данные сохранялись, но контент из плейлиста нигде не читался.
        playlistRepository = com.platinum.ott.data.repository.PlaylistRepository(authPreferences, database.playlistMovieDao(), okHttpClient)
        val tmdbClient = RetrofitFactory.createOkHttpClient(authPreferences, TmdbInterceptor())
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
        getPlayableUrlUseCase = GetPlayableUrlUseCase(scriptProvider, api, playlistRepository, authRepository)
        searchMoviesUseCase = SearchMoviesUseCase(movieRepository)
        clearCacheUseCase = ClearCacheUseCase(database.movieDao())
        // Раньше otaUpdateUseCase создавал СВОЙ отдельный OkHttpClient+api,
        // хотя api уже был построен строчкой выше — третий клиент на
        // пустом месте, без единой причины (не нужен отдельный interceptor,
        // как у tmdbClient). Переиспользуем уже готовый api.
        otaUpdateUseCase = OtaUpdateUseCase(scriptProvider, api)
        favoritesUseCase = FavoritesUseCase(database.favoritesDao())
        watchHistoryUseCase = WatchHistoryUseCase(database.watchHistoryDao())
        syncUseCase = SyncUseCase(syncRepository)
        seriesTrackerUseCase = SeriesTrackerUseCase(database.seriesScheduleDao(), tmdbRepository)
        pluginManager = com.platinum.ott.core.plugin.PluginManager(appContext, database.pluginDao(), pluginApi)
        pluginRepository = com.platinum.ott.core.plugin.PluginRepository(database.pluginDao(), pluginManager, pluginApi)
    }

    fun reinitWithAuth() {
        // Раньше пересоздавал только okHttpClient/api/movieRepository —
        // ~15 остальных зависимостей (playlistRepository, getPlayableUrlUseCase,
        // syncRepository, tmdbApi, use case'ы избранного/истории, вся система
        // плагинов) оставались привязаны к СТАРОМУ клиенту/учётным данным
        // после смены логина. initAuth() — единственный источник истины для
        // всего графа зависимостей; вызывать его повторно целиком не даёт
        // этому списку снова разойтись, как разошёлся в первый раз.
        // pluginManager.destroy() — закрывает живые QuickJs-инстансы
        // установленных плагинов перед тем, как pluginManager будет
        // пересоздан заново, иначе они бы просто повисли, не освобождённые.
        if (::pluginManager.isInitialized) pluginManager.destroy()
        initAuth()
    }
}
