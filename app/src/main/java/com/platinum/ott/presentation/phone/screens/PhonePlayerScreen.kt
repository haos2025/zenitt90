package com.platinum.ott.presentation.phone.screens

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlinx.coroutines.delay

// Раньше useController=true (дефолтный контроллер ExoPlayer) — работал, но
// без переключения качества (на TV это уже было) и без названия фильма во
// время просмотра. Теперь свой оверлей, как на TV, только под тач вместо
// D-pad — см. PhonePlayerController.kt.
@Composable
fun PhonePlayerScreen(movieId: String, navController: NavHostController, viewModel: PlayerViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(lastInteraction, showControls) {
        if (showControls) { delay(3000); showControls = false }
    }
    LaunchedEffect(uiState) {
        while (uiState is PlayerUiState.Ready) {
            currentPositionMs = viewModel.exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { showControls = !showControls; lastInteraction = System.currentTimeMillis() }
    ) {
        AndroidView(factory = { PlayerView(it).apply { player = viewModel.exoPlayer; useController = false } }, Modifier.fillMaxSize())
        when (val state = uiState) {
            is PlayerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PlayerUiState.Error -> Column(Modifier.align(Alignment.Center)) {
                Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
                Row {
                    Button(onClick = { navController.popBackStack() }) { Text("Назад") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.loadMovie(movieId) }) { Text("Повторить") }
                }
            }
            is PlayerUiState.Ready -> {
                PhonePlayerController(
                    isVisible = showControls,
                    isPlaying = isPlaying,
                    title = state.title,
                    currentPositionMs = currentPositionMs,
                    durationMs = viewModel.exoPlayer.duration.coerceAtLeast(0L),
                    variants = state.variants,
                    currentVariant = state.currentVariant,
                    onSeekForward = { viewModel.seekForward(); lastInteraction = System.currentTimeMillis() },
                    onSeekBackward = { viewModel.seekBackward(); lastInteraction = System.currentTimeMillis() },
                    onSeekTo = { viewModel.seekTo(it); lastInteraction = System.currentTimeMillis() },
                    onTogglePlay = { viewModel.togglePlayPause(); lastInteraction = System.currentTimeMillis() },
                    onSelectVariant = { viewModel.selectQuality(it); lastInteraction = System.currentTimeMillis() },
                    onBackPressed = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
