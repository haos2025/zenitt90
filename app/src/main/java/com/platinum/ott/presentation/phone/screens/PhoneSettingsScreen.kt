package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsScreen(navController: NavHostController) {

Scaffold(bottomBar = { BottomBar(navController) }) { padding ->
    Column(Modifier.padding(padding).background(Color(0xFF101010)).padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Card(
            onClick = { navController.navigate("plugins") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Плагины", style = MaterialTheme.typography.titleMedium)
                Text("Каталог и управление плагинами", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            onClick = { navController.navigate("sync_pairing") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
                Text("Источник, синхронизация между устройствами", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Раньше это была карточка-заглушка из общего списка без единого
        // обработчика нажатия. "Качество по умолчанию" — единственная
        // реально существующая часть "Воспроизведения": QualityPreferences
        // уже читается в PlayerViewModel.loadMovie() как стартовое качество
        // для любого фильма. "Автовоспроизведение"/"Субтитры" НЕ сделаны —
        // в коде нет ни понятия "следующая серия", ни обработки субтитровых
        // дорожек вообще, это была бы иллюзия настройки без реальной фичи
        // за ней — сознательно оставлено на будущее, не выдумывается здесь.
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Воспроизведение", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                val qualityPrefs = remember { QualityPreferences(context) }
                val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
                var selectedQuality by remember { mutableStateOf(qualityPrefs.getSelectedQuality() ?: "Авто") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Качество по умолчанию", color = Color.White, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val next = qualityOptions[(qualityOptions.indexOf(selectedQuality) + 1) % qualityOptions.size]
                        if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
                        selectedQuality = next
                    }) { Text(selectedQuality) }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(16.dp)) { Text("Уведомления", style = MaterialTheme.typography.titleMedium); Text("Каналы, тихий режим", color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
        // Раньше это была ещё одна карточка без единого обработчика нажатия
        // из общего списка-заглушки. Тема теперь реально переключает
        // ZenithTheme (см. MainActivity.kt/ServiceLocator.darkThemeFlow) —
        // язык оставлен видимым, но намеренно не нажимается: реальный
        // языковой переключатель потребовал бы вынести весь текст
        // интерфейса в string-ресурсы (сейчас 0 использований stringResource
        // во всём проекте) — отдельная большая задача, не в этом заходе.
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Интерфейс", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val darkTheme by com.platinum.ott.core.ServiceLocator.darkThemeFlow.collectAsState()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Тёмная тема", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = darkTheme, onCheckedChange = { com.platinum.ott.core.ServiceLocator.setDarkTheme(it) })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Язык", color = Color.Gray, modifier = Modifier.weight(1f))
                    Text("Скоро", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

}

@Composable
private fun BottomBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(false, onClick = { navController.navigate("home") }, icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Главная") })
        NavigationBarItem(false, onClick = { navController.navigate("favorites") }, icon = { Icon(Icons.Default.Favorite, "Fav") }, label = { Text("Избранное") })
        NavigationBarItem(false, onClick = { navController.navigate("history") }, icon = { Icon(Icons.Default.History, "Hist") }, label = { Text("История") })
        NavigationBarItem(false, onClick = { navController.navigate("settings") }, icon = { Icon(Icons.Default.Settings, "Set") }, label = { Text("Настройки") })
    }
}
