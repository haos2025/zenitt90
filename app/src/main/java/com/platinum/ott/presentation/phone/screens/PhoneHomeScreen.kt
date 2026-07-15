package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.screens.home.HomeUiState
import com.platinum.ott.presentation.screens.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(navController: NavHostController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(true, onClick = {}, icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Главная") })
                NavigationBarItem(false, onClick = { navController.navigate("favorites") }, icon = { Icon(Icons.Default.Favorite, "Fav") }, label = { Text("Избранное") })
                NavigationBarItem(false, onClick = { navController.navigate("history") }, icon = { Icon(Icons.Default.History, "Hist") }, label = { Text("История") })
                NavigationBarItem(false, onClick = { navController.navigate("settings") }, icon = { Icon(Icons.Default.Settings, "Set") }, label = { Text("Настройки") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFF101010))) {
            when (val state = uiState) {
                is HomeUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is HomeUiState.Error -> Text("⚠ ${state.message}", Modifier.align(Alignment.Center), color = Color(0xFFFF6B6B))
                is HomeUiState.Success -> LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.movies, key = { it.id }) { MovieCard(movie = it, onClick = { navController.navigate("detail/${it.id}") }) }
                }
            }
        }
    }
}
