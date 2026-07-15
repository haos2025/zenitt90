package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(movieId: String, onBackPressed: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { player = viewModel.exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
        when (val state = uiState) {
            is PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), Alignment.Center) { Text("Подготовка...", color = Color.White) }
            is PlayerUiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) { Text("⚠ ${state.message}", color = Color(0xFFFF6B6B)); Button(onClick = { viewModel.loadMovie(movieId) }) { Text("Повторить") } }
            is PlayerUiState.Ready -> { /* Quality button, controls */ }
        }
    }
}
