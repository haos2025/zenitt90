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
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneQrScanScreen(navController: NavHostController) {

Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text("Сканирование QR-кода", color = Color.White, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text("Наведите камеру на QR-код на экране TV", color = Color.Gray)
    // CameraX preview would go here with ML Kit barcode scanning
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
