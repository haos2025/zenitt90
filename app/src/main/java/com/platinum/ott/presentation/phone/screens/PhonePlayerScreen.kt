package com.platinum.ott.presentation.phone.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.player.PlayerUiState
import com.platinum.ott.presentation.screens.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Раньше useController=true (дефолтный контроллер ExoPlayer) — работал, но
// без переключения качества (на TV это уже было) и без названия фильма во
// время просмотра. Теперь свой оверлей, как на TV, только под тач вместо
// D-pad — см. PhonePlayerController.kt.
@Composable
fun PhonePlayerScreen(movieId: String, navController: NavHostController, preferredVariantUrl: String? = null, viewModel: PlayerViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId, preferredVariantUrl) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    // Раньше MainActivity форсила SCREEN_ORIENTATION_PORTRAIT для не-TV
    // безусловно, и ничего на экране плеера это не переопределяло — видео
    // физически не могло открыться на весь экран в альбомной ориентации.
    // Тогда же решили сразу форсить landscape-fullscreen при входе в
    // плеер — но по факту это означало, что плеер ВСЕГДА открывался на
    // весь экран с первого кадра, даже если пользователь просто хотел
    // посмотреть в портрете (как остальной интерфейс). Пересмотрено
    // (PROMPT_PLAYER_OVERLAY_REDESIGN.md): по умолчанию — портрет, как и
    // весь остальной UI; на весь экран/landscape — только по явному
    // действию (кнопка fullscreen ниже, либо физический поворот телефона,
    // если на нём уже завязана системная логика через сенсор).
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    // Восстановление при уходе с экрана плеера целиком (не при каждом
    // переключении isFullscreen) — иначе весь остальной телефонный UI
    // останется в альбомной ориентации без статус-бара.
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    LaunchedEffect(lastInteraction, showControls) {
        if (showControls) { delay(3000); showControls = false }
    }
    LaunchedEffect(uiState) {
        while (uiState is PlayerUiState.Ready) {
            currentPositionMs = viewModel.exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Раньше на экране плеера не было вообще никаких жестов — ни
    // громкости/яркости свайпом по краям экрана, ни перемотки свайпом
    // поперёк. Зона определяется ТОЧКОЙ НАЧАЛА жеста (не текущей позиции
    // пальца), иначе свайп, начатый слева и продолжающийся вправо, на
    // середине "перескакивал" бы с яркости на громкость.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var gestureIndicator by remember { mutableStateOf<GestureIndicator?>(null) }
    val gestureScope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(viewModel) {
            var mode: GestureMode? = null
            var accumulatedDeltaX = 0f
            var volumeFraction = 0f
            var seekStartMs = 0L
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

            kotlinx.coroutines.coroutineScope {
                launch { detectTapGestures(onTap = { showControls = !showControls; lastInteraction = System.currentTimeMillis() }) }
                launch {
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = null
                            accumulatedDeltaX = 0f
                            volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
                            seekStartMs = viewModel.exoPlayer.currentPosition
                            mode = if (offset.x < size.width / 2f) GestureMode.PENDING_LEFT else GestureMode.PENDING_RIGHT
                        },
                        onDragEnd = {
                            if (mode == GestureMode.SEEK) {
                                val targetMs = (seekStartMs + seekDeltaFromDrag(accumulatedDeltaX, size.width, viewModel.exoPlayer.duration)).coerceAtLeast(0)
                                viewModel.seekTo(targetMs)
                            }
                            mode = null
                            lastInteraction = System.currentTimeMillis()
                            gestureScope.launch { delay(600); gestureIndicator = null }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val horizontalWins = abs(dragAmount.x) > abs(dragAmount.y) * 1.5f
                            when (mode) {
                                GestureMode.PENDING_LEFT -> mode = if (horizontalWins) GestureMode.SEEK else GestureMode.BRIGHTNESS
                                GestureMode.PENDING_RIGHT -> mode = if (horizontalWins) GestureMode.SEEK else GestureMode.VOLUME
                                else -> {}
                            }
                            when (mode) {
                                GestureMode.BRIGHTNESS -> {
                                    activity?.window?.let { window ->
                                        val current = window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                                        val updated = (current - dragAmount.y / size.height).coerceIn(0.02f, 1f)
                                        window.attributes = window.attributes.apply { screenBrightness = updated }
                                        gestureIndicator = GestureIndicator.Brightness(updated)
                                    }
                                }
                                GestureMode.VOLUME -> {
                                    // Раньше громкость двигалась максимум на ±1 шаг за
                                    // событие драга, с порогом отсечения — на фоне
                                    // непрерывной яркости это ощущалось "вязким",
                                    // жест как будто не всегда срабатывал. Теперь
                                    // считается так же непрерывно, как яркость —
                                    // копим дробную долю (0..1), а не целые шаги.
                                    volumeFraction = (volumeFraction - dragAmount.y / size.height).coerceIn(0f, 1f)
                                    val target = (volumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                    if (target != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    }
                                    gestureIndicator = GestureIndicator.Volume(volumeFraction)
                                }
                                GestureMode.SEEK -> {
                                    accumulatedDeltaX += dragAmount.x
                                    val deltaMs = seekDeltaFromDrag(accumulatedDeltaX, size.width, viewModel.exoPlayer.duration)
                                    gestureIndicator = GestureIndicator.Seek(deltaMs, (seekStartMs + deltaMs).coerceAtLeast(0))
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    ) {
        AndroidView(factory = { PlayerView(it).apply { player = viewModel.exoPlayer; useController = false; keepScreenOn = true } }, Modifier.fillMaxSize())
        gestureIndicator?.let { GestureIndicatorOverlay(it, Modifier.align(Alignment.Center)) }
        when (val state = uiState) {
            is PlayerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PlayerUiState.Error -> Column(Modifier.align(Alignment.Center)) {
                Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                Row {
                    Button(onClick = { navController.popBackStack() }) { Text("Назад") }
                    Spacer(Modifier.width(ZenithDimens.paddingS))
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
                    audioTracks = state.audioTracks,
                    subtitleTracks = state.subtitleTracks,
                    subtitlesEnabled = state.subtitlesEnabled,
                    onSeekForward = { viewModel.seekForward(); lastInteraction = System.currentTimeMillis() },
                    onSeekBackward = { viewModel.seekBackward(); lastInteraction = System.currentTimeMillis() },
                    onSeekTo = { viewModel.seekTo(it); lastInteraction = System.currentTimeMillis() },
                    onTogglePlay = { viewModel.togglePlayPause(); lastInteraction = System.currentTimeMillis() },
                    onSelectVariant = { viewModel.selectQuality(it); lastInteraction = System.currentTimeMillis() },
                    onSelectAudio = { viewModel.selectAudioTrack(it); lastInteraction = System.currentTimeMillis() },
                    onSelectSubtitle = { viewModel.selectSubtitleTrack(it); lastInteraction = System.currentTimeMillis() },
                    onDisableSubtitles = { viewModel.disableSubtitles(); lastInteraction = System.currentTimeMillis() },
                    onLoadExternalSubtitle = { viewModel.loadExternalSubtitle(it); lastInteraction = System.currentTimeMillis() },
                    playbackSpeed = state.playbackSpeed,
                    onSelectSpeed = { viewModel.setPlaybackSpeed(it); lastInteraction = System.currentTimeMillis() },
                    onBackPressed = { navController.popBackStack() },
                    hasNextEpisode = state.nextEpisodeId != null,
                    hasPreviousEpisode = state.previousEpisodeId != null,
                    onNextEpisode = { viewModel.playNextEpisode(); lastInteraction = System.currentTimeMillis() },
                    onPreviousEpisode = { viewModel.playPreviousEpisode(); lastInteraction = System.currentTimeMillis() },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen; lastInteraction = System.currentTimeMillis() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private enum class GestureMode { PENDING_LEFT, PENDING_RIGHT, BRIGHTNESS, VOLUME, SEEK }

// Полный свайп через весь экран по горизонтали = 90 секунд — тот же
// порядок величины, что у большинства видеоплееров (не завязано на
// длительность ролика жёстко, иначе на 20-минутном эпизоде и на
// 3-часовом фильме один и тот же свайп мотал бы на совершенно разное
// количество процентов).
private fun seekDeltaFromDrag(accumulatedDeltaX: Float, screenWidthPx: Int, durationMs: Long): Long {
    val fraction = accumulatedDeltaX / screenWidthPx
    val maxSeekMs = 90_000L
    return (fraction * maxSeekMs).toLong().coerceIn(-durationMs.coerceAtLeast(1), durationMs.coerceAtLeast(1))
}

private sealed interface GestureIndicator {
    data class Brightness(val value: Float) : GestureIndicator
    data class Volume(val value: Float) : GestureIndicator
    data class Seek(val deltaMs: Long, val targetMs: Long) : GestureIndicator
}

@Composable
private fun GestureIndicatorOverlay(indicator: GestureIndicator, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (indicator) {
            is GestureIndicator.Brightness -> {
                Icon(Icons.Default.BrightnessHigh, null, tint = Color.White)
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Text("${(indicator.value * 100).roundToInt()}%", color = Color.White)
            }
            is GestureIndicator.Volume -> {
                Icon(Icons.Default.VolumeUp, null, tint = Color.White)
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Text("${(indicator.value * 100).roundToInt()}%", color = Color.White)
            }
            is GestureIndicator.Seek -> {
                val sign = if (indicator.deltaMs >= 0) "+" else "-"
                Text("$sign${formatSeekMs(abs(indicator.deltaMs))}  →  ${formatSeekMs(indicator.targetMs)}", color = Color.White)
            }
        }
    }
}

private fun formatSeekMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60; val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
