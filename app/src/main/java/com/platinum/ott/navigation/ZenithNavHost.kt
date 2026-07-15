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

@Composable
fun ZenithNavHost(startDestination: String, isTV: Boolean, modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("setup") { if (isTV) SetupScreen(onSetupComplete = { navController.navigate("home") { popUpTo(0) } }) else PhoneSetupRoute(navController) }
        composable("home") { if (isTV) HomeScreen(onMovieClick = { navController.navigate("detail/$it") }, onSettingsClick = { navController.navigate("settings") }, onFavoritesClick = { navController.navigate("favorites") }, onHistoryClick = { navController.navigate("history") }) else PhoneHomeScreen(navController) }
        composable("detail/{movieId}", arguments = listOf(navArgument("movieId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            if (isTV) DetailScreen(movieId = id, onPlayClick = { navController.navigate("player/$id") }, onBackPressed = { navController.popBackStack() }) else PhoneDetailScreen(id, navController)
        }
        composable("player/{movieId}", arguments = listOf(navArgument("movieId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("movieId") ?: return@composable
            if (isTV) PlayerScreen(movieId = id, onBackPressed = { navController.popBackStack() }) else PhonePlayerScreen(id, navController)
        }
        composable("settings") { if (isTV) SettingsScreen(onClearCacheClick = {}, onForceOtaUpdateClick = {}, onLogoutClick = { navController.navigate("setup") { popUpTo(0) } }, onPluginsClick = { navController.navigate("plugins") }) else PhoneSettingsScreen(navController) }
        composable("favorites") { if (isTV) FavoritesScreen(onBackPressed = { navController.popBackStack() }, onMovieClick = { navController.navigate("detail/$it") }) else PhoneFavoritesScreen(navController) }
        composable("history") { if (isTV) HistoryScreen(onBackPressed = { navController.popBackStack() }, onMovieClick = { navController.navigate("detail/$it") }) else PhoneHistoryScreen(navController) }
        composable("qr_scan") { if (isTV) Box(Modifier.fillMaxSize()) else PhoneQrScanScreen(navController) }
        // Plugin screens
        composable("plugins") { if (isTV) PluginCatalogScreen(onBackPressed = { navController.popBackStack() }, onPluginClick = { navController.navigate("plugin/$it") }) else PhonePluginCatalogScreen(navController) }
        composable("plugin/{pluginId}", arguments = listOf(navArgument("pluginId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("pluginId") ?: return@composable
            if (isTV) PluginDetailScreen(pluginId = id, onBackPressed = { navController.popBackStack() }) else PhonePluginDetailScreen(id, navController)
        }
    }
}

@Composable
private fun PhoneSetupRoute(navController: NavHostController) {
    com.platinum.ott.presentation.phone.screens.PhoneSetupScreen(onSetupComplete = { navController.navigate("home") { popUpTo(0) } })
}
