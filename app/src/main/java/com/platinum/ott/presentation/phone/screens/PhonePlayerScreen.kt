package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.player.PlayerUiState
import com.platinum.ott.presentation.screens.player.PlayerViewModel

@Composable
fun PhonePlayerScreen(movieId: String, navController: NavHostController, viewModel: PlayerViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { player = viewModel.exoPlayer; useController = true } }, Modifier.fillMaxSize())
        when (val state = uiState) {
            is PlayerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PlayerUiState.Error -> Column(Modifier.align(Alignment.Center)) { Text("⚠ ${state.message}", color = Color(0xFFFF6B6B)); Button(onClick = { viewModel.loadMovie(movieId) }) { Text("Повторить") } }
            is PlayerUiState.Ready -> { /* Touch controls handled by PlayerView */ }
        }
    }
}
