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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHistoryScreen(navController: NavHostController) {

val viewModel: com.platinum.ott.presentation.screens.history.HistoryViewModel = viewModel()
val history by viewModel.history.collectAsState(initial = emptyList())
Scaffold(bottomBar = { BottomBar(navController) }) { padding ->
    LazyColumn(Modifier.padding(padding).background(Color(0xFF101010)), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history, key = { it.contentId }) { entry ->
            val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(entry.title, color = Color.White); LinearProgressIndicator({ p }) } } }
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
