package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.navigation.navigateToTab

// PROMPT_NAVIGATION_SIDEBAR.md — постоянный боковой сайдбар на TV, тот же
// принцип, что уже применён в PhoneBottomBar.kt: один общий компонент,
// currentBackStackEntryAsState() для подсветки активного пункта, а не
// локальная копия на каждом экране (раньше HomeScreen.kt держал свой ряд
// кнопок только у себя — с "Избранного" нельзя было попасть в "Историю"
// напрямую, только через возврат на "Главную").
//
// Без "Источники" — тот остаётся достижим только через Настройки, как
// решили. Без "Сериалы" — на Home эту роль теперь играет вкладка-фильтр
// в самой ленте (см. PROMPT_HOME_FEED_REDESIGN.md/HomeScreen.kt), отдельного
// пункта под browse-всех-сериалов в сайдбаре нет.
private enum class SidebarItem(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Главная", Icons.Default.Home),
    SEARCH("search", "Поиск", Icons.Default.Search),
    FAVORITES("favorites", "Избранное", Icons.Default.Favorite),
    HISTORY("history", "История", Icons.Default.History),
    SETTINGS("settings", "Настройки", Icons.Default.Settings),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavSidebar(navController: NavHostController, modifier: Modifier = Modifier) {
    // "search" зарегистрирован в ZenithNavHost как "search?q={q}" —
    // текущий route в этом случае буквально "search?q={q}", не "search",
    // substringBefore('?') сравнивает по базовому пути, не по полной сигнатуре.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')

    Column(
        modifier = modifier.fillMaxHeight().width(88.dp).background(MaterialTheme.colorScheme.surface).padding(vertical = ZenithDimens.paddingL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SidebarItem.values().forEach { item ->
            val selected = currentRoute == item.route
            Surface(
                onClick = { if (!selected) navController.navigateToTab(item.route) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.padding(vertical = ZenithDimens.paddingS).size(56.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = item.label, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
                }
            }
        }
        // Задел под будущий переключатель режимов интерфейса (по аналогии с
        // Lampa) — сознательно не занимаем это место другим пунктом
        // навигации, чтобы потом было куда добавить. Сам переключатель не
        // реализуется в этой сессии.
        Spacer(Modifier.weight(1f))
    }
}
