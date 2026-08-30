package com.platinum.ott.presentation.phone.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.platinum.ott.navigation.navigateToTab

// Раньше каждый экран (Home/History/Favorites/Settings) держал СВОЮ копию
// этой панели — 5 штук в пяти файлах. Везде, кроме Home, `selected` был
// жёстко прописан как false для всех кнопок сразу — панель физически не
// знала, на каком экране находится пользователь, поэтому подсветка нигде,
// кроме Home, никогда не работала. Одна общая панель, читающая реальный
// текущий route через currentBackStackEntryAsState(), чинит это в одном
// месте, а не в пяти.
@Composable
fun PhoneBottomBar(navController: NavHostController) {
    // "search" зарегистрирован в ZenithNavHost как "search?q={q}" —
    // текущий route в этом случае буквально "search?q={q}", не "search".
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigateToTab("home") },
            icon = { Icon(Icons.Default.Home, "Главная") },
            label = { Text("Главная") }
        )
        // PROMPT_NAVIGATION_SIDEBAR.md, п.2 — "Поиск" вторым пунктом (после
        // "Главная", перед "Избранное"): самое частое действие после захода
        // в приложение, логично держать в начале, не в конце ряда.
        NavigationBarItem(
            selected = currentRoute == "search",
            onClick = { navController.navigateToTab("search") },
            icon = { Icon(Icons.Default.Search, "Поиск") },
            label = { Text("Поиск") }
        )
        NavigationBarItem(
            selected = currentRoute == "favorites",
            onClick = { navController.navigateToTab("favorites") },
            icon = { Icon(Icons.Default.Favorite, "Избранное") },
            label = { Text("Избранное") }
        )
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick = { navController.navigateToTab("history") },
            icon = { Icon(Icons.Default.History, "История") },
            label = { Text("История") }
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick = { navController.navigateToTab("settings") },
            icon = { Icon(Icons.Default.Settings, "Настройки") },
            label = { Text("Настройки") }
        )
    }
}
