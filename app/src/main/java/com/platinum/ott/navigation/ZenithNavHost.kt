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
import com.platinum.ott.presentation.screens.setup.SetupScreen
import com.platinum.ott.presentation.screens.favorites.FavoritesScreen
import com.platinum.ott.presentation.screens.history.HistoryScreen
import com.platinum.ott.presentation.screens.settings.SettingsScreen
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

@Composable
fun ZenithNavHost(startDestination: String, isTV: Boolean, modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("setup") {
            if (isTV) SetupScreen(
                onSetupComplete = { navController.navigate("home") { popUpTo(0) } },
                onSettingsClick = { navController.navigateToTab("settings") },
                onHistoryClick = { navController.navigateToTab("history") },
                onFavoritesClick = { navController.navigateToTab("favorites") }
            ) else PhoneSetupRoute(navController)
        }
        composable("home") { if (isTV) HomeScreen(onMovieClick = { navController.navigate("detail/$it") }, onSettingsClick = { navController.navigateToTab("settings") }, onFavoritesClick = { navController.navigateToTab("favorites") }, onHistoryClick = { navController.navigateToTab("history") }, onSearchClick = { navController.navigateToTab("search") }, onSeriesClick = { navController.navigateToTab("series") }) else PhoneHomeScreen(navController) }
        composable("detail/{movieId}", arguments = listOf(navArgument("movieId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            if (isTV) DetailScreen(movieId = id, onPlayClick = { navController.navigate("player/$id") }, onBackPressed = { navController.popBackStack() }) else PhoneDetailScreen(id, navController)
        }
        composable("player/{movieId}", arguments = listOf(navArgument("movieId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            if (isTV) PlayerScreen(movieId = id, onBackPressed = { navController.popBackStack() }) else PhonePlayerScreen(id, navController)
        }
        composable("settings") { if (isTV) SettingsScreen(onClearCacheClick = {}, onForceOtaUpdateClick = {}, onLogoutClick = { navController.navigate("setup") { popUpTo(0) } }, onPluginsClick = { navController.navigate("plugins") }, onSyncClick = { navController.navigate("sync_pairing") }, onConnectSourceClick = { navController.navigate("setup") }) else PhoneSettingsScreen(navController) }
        composable("sync_pairing") { SyncPairingScreen(onBackPressed = { navController.popBackStack() }) }
        composable("favorites") { if (isTV) FavoritesScreen(onBackPressed = { navController.popBackStack() }, onItemClick = { fav -> if (fav.contentType == "SERIES") navController.navigate("series/${fav.contentId}") else navController.navigate("detail/${fav.contentId}") }) else PhoneFavoritesScreen(navController) }
        composable("history") { if (isTV) HistoryScreen(onBackPressed = { navController.popBackStack() }, onMovieClick = { navController.navigate("detail/$it") }) else PhoneHistoryScreen(navController) }
        composable("qr_scan") { if (isTV) Box(Modifier.fillMaxSize()) else PhoneQrScanScreen(navController) }
        // Plugin screens
        composable("plugins") { if (isTV) PluginCatalogScreen(onBackPressed = { navController.popBackStack() }, onPluginClick = { navController.navigate("plugin/$it") }) else PhonePluginCatalogScreen(navController) }
        composable("search?q={q}", arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { entry ->
            val q = entry.arguments?.getString("q") ?: ""
            if (isTV) com.platinum.ott.presentation.screens.search.SearchScreen(onBackPressed = { navController.popBackStack() }, onMovieClick = { navController.navigate("detail/$it") }, initialQuery = q)
            else PhoneSearchScreen(navController, initialQuery = q)
        }
        composable("series") {
            if (isTV) com.platinum.ott.presentation.screens.series.SeriesListScreen(onBackPressed = { navController.popBackStack() }, onSeriesClick = { navController.navigate("series/$it") })
            else PhoneSeriesListScreen(navController)
        }
        composable("series/{seriesId}", arguments = listOf(navArgument("seriesId") { type = NavType.StringType })) { entry ->
            val seriesId = entry.arguments?.getString("seriesId") ?: return@composable
            if (isTV) com.platinum.ott.presentation.screens.series.SeriesEpisodesScreen(seriesId = seriesId, onBackPressed = { navController.popBackStack() }, onEpisodeClick = { navController.navigate("player/$it") })
            else PhoneSeriesEpisodesScreen(seriesId = seriesId, navController = navController)
        }
        composable("plugin/{pluginId}", arguments = listOf(navArgument("pluginId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("pluginId") ?: return@composable
            if (isTV) PluginDetailScreen(pluginId = id, onBackPressed = { navController.popBackStack() }) else PhonePluginDetailScreen(id, navController)
        }
    }
}

@Composable
private fun PhoneSetupRoute(navController: NavHostController) {
    com.platinum.ott.presentation.phone.screens.PhoneSetupScreen(
        onSetupComplete = { navController.navigate("home") { popUpTo(0) } },
        onSettingsClick = { navController.navigateToTab("settings") },
        onHistoryClick = { navController.navigateToTab("history") },
        onFavoritesClick = { navController.navigateToTab("favorites") }
    )
}
