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
    fun init(ctx: Context) { appContext = ctx.applicationContext; initAuth() }

    // --- Database ---
    val database: ZenithDatabase by lazy { ZenithDatabase.getInstance(appContext) }

    // --- Auth ---
    val authPreferences: AuthPreferences by lazy { AuthPreferences(appContext) }
    lateinit var authRepository: AuthRepository; private set
    lateinit var movieRepository: MovieRepository; private set
    lateinit var tmdbApi: TmdbApiService; private set
    lateinit var tmdbRepository: TmdbRepository; private set
    lateinit var syncRepository: SyncRepository; private set
    lateinit var checkAuthUseCase: CheckAuthUseCase; private set
    lateinit var loginM3UUseCase: LoginM3UUseCase; private set
    lateinit var loginXtreamUseCase: LoginXtreamUseCase; private set
    lateinit var logoutUseCase: LogoutUseCase; private set
    lateinit var getCatalogUseCase: GetCatalogUseCase; private set
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
    lateinit var pluginViewModel: com.platinum.ott.presentation.screens.plugins.PluginViewModel; private set

    val scriptProvider: ScriptProvider by lazy { ScriptProvider(appContext) }
    val pluginApi: com.platinum.ott.core.plugin.PluginApi by lazy { com.platinum.ott.core.plugin.PluginApi(appContext) }

    fun initAuth() {
        val okHttpClient = RetrofitFactory.createOkHttpClient(authPreferences)
        val api = RetrofitFactory.createApi(okHttpClient)
        authRepository = AuthRepositoryImpl(authPreferences, okHttpClient)
        movieRepository = MovieRepositoryImpl(api, database.movieDao())
        val tmdbClient = RetrofitFactory.createOkHttpClient(authPreferences, TmdbInterceptor())
        tmdbApi = RetrofitFactory.createTmdbApi(tmdbClient)
        tmdbRepository = TmdbRepositoryImpl(tmdbApi, database.metadataDao())
        syncRepository = SyncRepositoryImpl(api, database.favoritesDao(), database.watchHistoryDao(), authPreferences)
        checkAuthUseCase = CheckAuthUseCase(authRepository)
        loginM3UUseCase = LoginM3UUseCase(authRepository)
        loginXtreamUseCase = LoginXtreamUseCase(authRepository)
        logoutUseCase = LogoutUseCase(authRepository)
        getCatalogUseCase = GetCatalogUseCase(movieRepository)
        getMovieByIdUseCase = GetMovieByIdUseCase(movieRepository)
        getPlayableUrlUseCase = GetPlayableUrlUseCase(scriptProvider, api, authRepository)
        searchMoviesUseCase = SearchMoviesUseCase(movieRepository)
        clearCacheUseCase = ClearCacheUseCase(database.movieDao())
        otaUpdateUseCase = OtaUpdateUseCase(scriptProvider, RetrofitFactory.createApi(RetrofitFactory.createOkHttpClient(authPreferences)))
        favoritesUseCase = FavoritesUseCase(database.favoritesDao())
        watchHistoryUseCase = WatchHistoryUseCase(database.watchHistoryDao())
        syncUseCase = SyncUseCase(syncRepository)
        seriesTrackerUseCase = SeriesTrackerUseCase(database.seriesScheduleDao(), tmdbRepository)
        pluginManager = com.platinum.ott.core.plugin.PluginManager(appContext, database.pluginDao(), pluginApi)
        pluginRepository = com.platinum.ott.core.plugin.PluginRepository(database.pluginDao(), pluginManager, pluginApi)
        pluginViewModel = com.platinum.ott.presentation.screens.plugins.PluginViewModel(pluginManager, pluginRepository)
    }

    fun reinitWithAuth() {
        val okHttpClient = RetrofitFactory.createOkHttpClient(authPreferences)
        val api = RetrofitFactory.createApi(okHttpClient)
        movieRepository = MovieRepositoryImpl(api, database.movieDao())
    }
}
