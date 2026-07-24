package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import kotlinx.coroutines.delay

/**
 * Раньше useController=false и пустая ветка Ready ("/* Quality button,
 * controls */") — видео проигрывалось совсем без управления. Теперь:
 *  - PlayerController (перемотка/пауза/прогресс-бар) — показывается по
 *    любому нажатию на пульте, автоскрытие через 3с бездействия.
 *  - QualityMenuOverlay — по кнопке Menu; пока открыт, ключевые события
 *    Up/Down/Center НЕ перехватываются здесь, чтобы его собственный
 *    LazyColumn нормально работал через встроенную фокус-навигацию Compose.
 *  - D-pad Center/OK и системная Play/Pause с пульта — пауза/воспроизведение.
 *  - Left/Right — перемотка на 10 секунд.
 *  - Back — если открыто меню качества, сначала закрывает его; иначе
 *    вызывает onBackPressed (раньше этот параметр не использовался вообще).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(movieId: String, onBackPressed: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    val readyState = uiState as? PlayerUiState.Ready

    // Автоскрытие контроллера через 3с бездействия, но не пока открыто меню качества
    LaunchedEffect(lastInteraction, readyState?.showQualityMenu) {
        if (showControls && readyState?.showQualityMenu != true) {
            delay(3000)
            showControls = false
        }
    }

    // У ExoPlayer нет готового Flow под текущую позицию — опрашиваем, пока экран Ready
    LaunchedEffect(uiState) {
        while (uiState is PlayerUiState.Ready) {
            currentPositionMs = viewModel.exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val ready = uiState as? PlayerUiState.Ready ?: return@onKeyEvent false

                if (ready.showQualityMenu) {
                    return@onKeyEvent when (event.key) {
                        Key.Back, Key.Menu -> { viewModel.dismissQualityMenu(); true }
                        else -> false // Up/Down/Center — отдаём QualityMenuOverlay
                    }
                }

                lastInteraction = System.currentTimeMillis()
                showControls = true
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                    Key.DirectionRight -> { viewModel.seekForward(); true }
                    Key.DirectionLeft -> { viewModel.seekBackward(); true }
                    Key.Menu -> { viewModel.toggleQualityMenu(); true }
                    Key.Back -> { onBackPressed(); true }
                    else -> false // любая другая кнопка — контроллер уже показан выше
                }
            }
    ) {
        AndroidView(factory = { PlayerView(it).apply { player = viewModel.exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
        when (val state = uiState) {
            is PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), Alignment.Center) { Text("Подготовка...", color = Color.White) }
            is PlayerUiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Раньше здесь была только кнопка "Повторить" — при
                        // ошибке вроде HTTP 404 (сломанная ссылка, не временный
                        // сбой) повтор просто получает ту же ошибку снова,
                        // а выйти можно было только системным жестом назад.
                        Button(onClick = { onBackPressed() }) { Text("Назад") }
                        Button(onClick = { viewModel.loadMovie(movieId) }) { Text("Повторить") }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                PlayerController(
                    isVisible = showControls && !state.showQualityMenu,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = viewModel.exoPlayer.duration.coerceAtLeast(0L),
                    onSeekForward = { viewModel.seekForward() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onTogglePlay = { viewModel.togglePlayPause() },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.showQualityMenu) {
                    QualityMenuOverlay(
                        variants = state.variants,
                        currentVariant = state.currentVariant,
                        onSelectVariant = { viewModel.selectQuality(it) },
                        onDismiss = { viewModel.dismissQualityMenu() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
