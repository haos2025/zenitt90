package com.platinum.ott.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.platinum.ott.presentation.screens.home.HomeScreen
import com.platinum.ott.presentation.screens.detail.DetailScreen
import com.platinum.ott.presentation.screens.player.PlayerScreen
import com.platinum.ott.presentation.screens.sources.SourcesScreen
import com.platinum.ott.presentation.screens.sources.AddSourceScreen
import com.platinum.ott.presentation.screens.favorites.FavoritesScreen
import com.platinum.ott.presentation.screens.history.HistoryScreen
import com.platinum.ott.presentation.screens.settings.SettingsScreen
import com.platinum.ott.presentation.screens.cache.CacheManagementScreen
import com.platinum.ott.presentation.screens.sync.SyncPairingScreen
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.presentation.screens.plugins.PluginCatalogScreen
import com.platinum.ott.presentation.screens.plugins.PluginDetailScreen
import com.platinum.ott.presentation.phone.screens.PhoneHomeScreen
import com.platinum.ott.presentation.phone.screens.PhoneDetailScreen
import com.platinum.ott.presentation.phone.screens.PhoneFavoritesScreen
import com.platinum.ott.presentation.phone.screens.PhoneHistoryScreen
import com.platinum.ott.presentation.phone.screens.PhoneSettingsScreen
import com.platinum.ott.presentation.phone.screens.PhonePlayerScreen
import com.platinum.ott.presentation.phone.screens.PhoneQrScanScreen
import com.platinum.ott.presentation.phone.screens.PhonePluginCatalogScreen
import com.platinum.ott.presentation.phone.screens.PhonePluginDetailScreen
import com.platinum.ott.presentation.phone.screens.PhoneSearchScreen
import com.platinum.ott.presentation.phone.screens.PhoneSeriesListScreen
import com.platinum.ott.presentation.phone.screens.PhoneSeriesEpisodesScreen
import com.platinum.ott.presentation.phone.screens.PhoneCacheManagementScreen
import com.platinum.ott.presentation.phone.screens.PhoneSourcesScreen
import com.platinum.ott.presentation.phone.screens.PhoneAddSourceScreen

@Composable
fun ZenithNavHost(startDestination: String, isTV: Boolean, modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        // Заменяет прежний "setup" (одноразовая настройка одного источника,
        // тупиковый route без нормальной навигации) — PROMPT_SOURCES_SCREEN.md.
        // "sources" достижим из Настроек в любой момент, не только при
        // первом запуске (startDestination всегда "home", см. MainActivity.kt
        // и комментарий у "home" ниже про NAVIGATION_SIDEBAR).
        composable("sources") {
            if (isTV) SourcesScreen(onBackPressed = { navController.popBackStack() }, onAddSourceClick = { navController.navigate("add_source") })
            else PhoneSourcesScreen(navController, onAddSourceClick = { navController.navigate("add_source") })
        }
        composable("add_source") {
            if (isTV) AddSourceScreen(onDone = { navController.popBackStack() })
            else PhoneAddSourceScreen(navController, onDone = { navController.popBackStack() })
        }
        // PROMPT_NAVIGATION_SIDEBAR.md — HomeScreen (TV) больше не получает
        // отдельные onSettingsClick/onFavoritesClick/onHistoryClick/
        // onSearchClick/onSeriesClick: локальный ряд кнопок убран целиком,
        // навигацию по вкладкам берёт на себя постоянный сайдбар
        // (NavSidebar.kt), которому нужен сам navController. "Сериалы" как
        // отдельный переход с Home не переехал в сайдбар — эту роль теперь
        // играет вкладка-фильтр внутри ленты (см. HomeScreen.kt).
        composable("home") { if (isTV) HomeScreen(navController = navController, onMovieClick = { navController.navigate("detail/$it") }) else PhoneHomeScreen(navController) }
        composable("detail/{movieId}", arguments = listOf(navArgument("movieId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            if (isTV) DetailScreen(
                movieId = id,
                onPlayClick = { navController.navigate("player/$id") },
                onBackPressed = { navController.popBackStack() },
                // Эпизод сериала — см. DetailViewModel.load()/DetailScreen.kt.
                // popUpTo убирает саму карточку-редирект из бэкстека — иначе
                // "Назад" с экрана сериала вернул бы на пустой промежуточный шаг.
                onNavigateToSeries = { seriesId -> navController.navigate("series/$seriesId") { popUpTo("detail/{movieId}") { inclusive = true } } }
            ) else PhoneDetailScreen(id, navController)
        }
        composable(
            "player/{movieId}?variantUrl={variantUrl}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                // Выбор озвучки/варианта в едином окне сериала (см.
                // SeriesEpisodesScreen.kt) — если у эпизода несколько
                // StreamVariant'ов, пользователь выбирает один ДО того, как
                // откроется плеер, а не после (плеер и так умеет
                // переключать качество/озвучку в процессе, но тут выбор
                // делается заранее, до старта воспроизведения). Пустая
                // строка вместо null — NavType.StringType в этой версии
                // Navigation Compose не поддерживает nullable по умолчанию
                // без доп. NavType, а пустая строка как "нет
                // предпочтения" не пересекается с реальными URL.
                navArgument("variantUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            val variantUrl = entry.arguments?.getString("variantUrl")?.ifBlank { null }
            if (isTV) PlayerScreen(movieId = id, preferredVariantUrl = variantUrl, onBackPressed = { navController.popBackStack() }) else PhonePlayerScreen(id, navController, preferredVariantUrl = variantUrl)
        }
        composable("settings") { if (isTV) SettingsScreen(navController = navController, onCacheManagementClick = { navController.navigate("cache_management") }, onForceOtaUpdateClick = {}, onPluginsClick = { navController.navigate("plugins") }, onSyncClick = { navController.navigate("sync_pairing") }, onSourcesClick = { navController.navigate("sources") }) else PhoneSettingsScreen(navController) }
        // PROMPT_CACHE_MANAGEMENT.md — заменяет прежнюю единственную кнопку
        // "Очистить кэш" на TV (её вообще не было на телефоне). Один и тот
        // же CacheManagementViewModel под обеими версиями UI.
        composable("cache_management") { if (isTV) CacheManagementScreen(onBackPressed = { navController.popBackStack() }) else PhoneCacheManagementScreen(navController) }
        composable("sync_pairing") { SyncPairingScreen(onBackPressed = { navController.popBackStack() }) }
        composable("favorites") { if (isTV) FavoritesScreen(navController = navController, onItemClick = { fav -> if (fav.contentType == "SERIES") navController.navigate("series/${fav.contentId}") else navController.navigate("detail/${fav.contentId}") }) else PhoneFavoritesScreen(navController) }
        composable("history") { if (isTV) HistoryScreen(navController = navController, onMovieClick = { navController.navigate("detail/$it") }) else PhoneHistoryScreen(navController) }
        composable("qr_scan") { if (isTV) Box(Modifier.fillMaxSize()) else PhoneQrScanScreen(navController) }
        // Plugin screens
        composable("plugins") { if (isTV) PluginCatalogScreen(onBackPressed = { navController.popBackStack() }, onPluginClick = { navController.navigate("plugin/$it") }) else PhonePluginCatalogScreen(navController) }
        composable("search?q={q}", arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { entry ->
            val q = entry.arguments?.getString("q") ?: ""
            if (isTV) com.platinum.ott.presentation.screens.search.SearchScreen(navController = navController, onBackPressed = { navController.popBackStack() }, onMovieClick = { navController.navigate("detail/$it") }, initialQuery = q)
            else PhoneSearchScreen(navController, initialQuery = q)
        }
        composable("series") {
            if (isTV) com.platinum.ott.presentation.screens.series.SeriesListScreen(onBackPressed = { navController.popBackStack() }, onSeriesClick = { navController.navigate("series/$it") })
            else PhoneSeriesListScreen(navController)
        }
        composable("series/{seriesId}", arguments = listOf(navArgument("seriesId") { type = NavType.StringType })) { entry ->
            val seriesId = entry.arguments?.getString("seriesId") ?: return@composable
            // navigateToPlayer — общий для TV/телефона способ собрать
            // "player/{id}?variantUrl=..." с корректным кодированием URL
            // (сам variantUrl может содержать "&"/"?" и т.д.).
            val navigateToPlayer: (String, String?) -> Unit = { episodeId, variantUrl ->
                val route = if (variantUrl != null) "player/$episodeId?variantUrl=${android.net.Uri.encode(variantUrl)}" else "player/$episodeId"
                navController.navigate(route)
            }
            if (isTV) com.platinum.ott.presentation.screens.series.SeriesEpisodesScreen(seriesId = seriesId, onBackPressed = { navController.popBackStack() }, onEpisodeClick = navigateToPlayer)
            else PhoneSeriesEpisodesScreen(seriesId = seriesId, navController = navController)
        }
        composable("plugin/{pluginId}", arguments = listOf(navArgument("pluginId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("pluginId") ?: return@composable
            if (isTV) PluginDetailScreen(pluginId = id, onBackPressed = { navController.popBackStack() }) else PhonePluginDetailScreen(id, navController)
        }
    }
}
