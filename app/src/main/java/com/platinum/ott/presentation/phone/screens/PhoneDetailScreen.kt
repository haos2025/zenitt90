package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.platinum.ott.domain.model.TmdbMetadata
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.detail.DetailUiState
import com.platinum.ott.presentation.screens.detail.DetailViewModel

@Composable
fun PhoneDetailScreen(movieId: String, navController: NavHostController, viewModel: DetailViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Color(0xFF101010)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            is DetailUiState.Error -> Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
            is DetailUiState.Success -> {
                Text(state.movie.title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                state.metadata?.let { m ->
                    m.genres?.let { Text(it, color = Color.Gray) }
                    m.voteAverage?.let { Text("★ $it", color = Color(0xFFFFC107)) }
                    m.overview?.let { Text(it, color = Color.White.copy(0.8f), Modifier.padding(top = 8.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navController.navigate("player/$movieId") }, modifier = Modifier.weight(1f)) { Text(if (state.watchProgress != null) "Продолжить" else "Смотреть") }
                    OutlinedButton(onClick = { viewModel.toggleFavorite(movieId, state.movie.title, state.movie.poster) }) { Text(if (state.isFavorite) "♥" else "♡") }
                }
            }
        }
    }
}
