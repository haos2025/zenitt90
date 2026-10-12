package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.platinum.ott.navigation.navigateToTab

// Нижняя панель телефона (по макету): Главная · Каналы · Поиск · Избранное · Ещё.
// «Ещё» открывает лист с остальным: Сериалы, Телепрограмма, История, Настройки.
// Одна общая панель читает реальный route через currentBackStackEntryAsState(),
// поэтому подсветка работает на любом экране (раньше у каждого экрана была своя
// копия с жёстко прописанным selected = false).
private data class MoreItem(val route: String, val label: String, val icon: ImageVector)

private val MoreItems = listOf(
    MoreItem("series", "Сериалы", Icons.Default.Movie),
    MoreItem("epg_grid", "Телепрограмма", Icons.Default.CalendarMonth),
    MoreItem("history", "История", Icons.Default.History),
    MoreItem("settings", "Настройки", Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBottomBar(navController: NavHostController) {
    // "search" зарегистрирован как "search?q={q}" — сравниваем по базовому пути.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')
    var showMore by remember { mutableStateOf(false) }
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigateToTab("home") },
            icon = { Icon(Icons.Default.Home, "Главная") },
            label = { NavLabel("Главная") },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == "channels",
            onClick = { navController.navigateToTab("channels") },
            icon = { Icon(Icons.Default.Tv, "Каналы") },
            label = { NavLabel("Каналы") },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == "search",
            onClick = { navController.navigateToTab("search") },
            icon = { Icon(Icons.Default.Search, "Поиск") },
            label = { NavLabel("Поиск") },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == "favorites",
            onClick = { navController.navigateToTab("favorites") },
            icon = { Icon(Icons.Default.Favorite, "Избранное") },
            label = { NavLabel("Избранное") },
            colors = colors,
        )
        NavigationBarItem(
            selected = MoreItems.any { it.route == currentRoute },
            onClick = { showMore = true },
            icon = { Icon(Icons.Default.Menu, "Ещё") },
            label = { NavLabel("Ещё") },
            colors = colors,
        )
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(Modifier.navigationBarsPadding()) {
                MoreItems.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.label) },
                        leadingContent = { Icon(item.icon, null) },
                        modifier = Modifier.clickable {
                            showMore = false
                            if (currentRoute != item.route) navController.navigateToTab(item.route)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Оборачивает экран, у которого нет своей нижней панели (Каналы, Телепрограмма,
 * Сериалы): контент сверху, панель снизу. Нижний системный отступ «потреблён»
 * панелью, чтобы Scaffold внутри экрана не добавил второй такой же.
 */
@Composable
fun PhoneWithBottomBar(navController: NavHostController, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
        ) { content() }
        PhoneBottomBar(navController)
    }
}

// maxLines=1: при пяти пунктах длинные подписи на узком экране иначе
// переносились на вторую строку обрубком — лучше многоточие.
@Composable
private fun NavLabel(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
}
