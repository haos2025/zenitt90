package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.platinum.ott.core.companion.CompanionHttpServer
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.ui.theme.ZenithSurface
import androidx.tv.material3.*
import kotlinx.coroutines.delay

/**
 * Раньше useController=false и пустая ветка Ready (\"/* Quality button,
 * controls */\") — видео проигрывалось совсем без управления. Теперь:
 *  - PlayerController (пауза/прогресс-бар) — показывается по
 *    любому нажатию на пульте, автоскрытие через 3с бездействия.
 *  - PlaybackMenuOverlay (качество/аудио/субтитры/скорость) — по кнопке Menu; пока открыт, ключевые события
 *    Up/Down/Center НЕ перехватываются здесь, чтобы его собственный
 *    LazyColumn нормально работал через встроенную фокус-навигацию Compose.
 *  - D-pad Center/OK и системная Play/Pause с пульта — пауза/воспроизведение.
 *  - Left/Right — перемотка на 10 секунд (или переключение серии, если
 *    сериал) — сопровождается короткой надписью по центру экрана
 *    (seekToast), которая ненадолго появляется и гаснет.
 *  - Back — если открыто меню качества, сначала закрывает его; иначе
 *    вызывает onBackPressed.
 *
 * Третий раунд редизайна (убраны декоративные ±10с кнопки в
 * PlayerController — см. комментарий там, сам key handler ниже не
 * менялся в части перемотки, только добавлено отслеживание "держит ли
 * пользователь Left/Right сейчас" для растущего прогресс-бара):
 * isDpadSeekActive считается по факту получения KeyDown-события
 * DirectionLeft/DirectionRight (Android сам шлёт повторные KeyDown при
 * удержании клавиши на пульте) — включается сразу, гаснет через 400ms
 * после последнего такого события, тем же паттерном debounce, что уже
 * используется для seekToast/showControls ниже.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(movieId: String, onBackPressed: () -> Unit, preferredVariantUrl: String? = null, viewModel: PlayerViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId, preferredVariantUrl) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(0L) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Кнопки перемотки/эпизодов в капсуле — decorative (см.
    // PlayerController.kt: не получают фокус пульта напрямую, реальное
    // действие идёт через DirectionLeft/DirectionRight ниже). Короткая
    // полупрозрачная надпись по центру экрана ("10 сек" / название серии),
    // тот же паттерн автоскрытия, что уже используется для showControls.
    var seekToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekToast) {
        if (seekToast != null) {
            delay(700)
            seekToast = null
        }
    }

    // Растущий прогресс-бар (PlayerController.ProgressBar, isSeekActive) —
    // seekPulseNonce увеличивается на каждое реальное нажатие
    // Left/Right-перемотки (не переключение серии — там позиция скачком
    // меняется на новую серию, "разбухание" бара в момент скачка не несёт
    // смысла скраббинга, поэтому не триггерим). LaunchedEffect перезапускается
    // на каждое новое значение nonce, поэтому при удержании клавиши
    // (повторные KeyDown от Android) индикатор остаётся включённым
    // непрерывно, гаснет только через 400ms после последнего события.
    var seekPulseNonce by remember { mutableStateOf(0) }
    var isDpadSeekActive by remember { mutableStateOf(false) }
    LaunchedEffect(seekPulseNonce) {
        if (seekPulseNonce > 0) {
            isDpadSeekActive = true
            delay(400)
            isDpadSeekActive = false
        }
    }

    // Телефон-компаньон (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) —
    // сервер живёт ровно пока открыт этот оверлей, не дольше: DisposableEffect
    // на showCompanionQr стартует его при открытии и гарантированно
    // останавливает при закрытии (отменой, получением URL или уходом с экрана).
    var showCompanionQr by remember { mutableStateOf(false) }
    var companionAddress by remember { mutableStateOf<String?>(null) }
    DisposableEffect(showCompanionQr) {
        var server: CompanionHttpServer? = null
        if (showCompanionQr) {
            server = CompanionHttpServer(endpointPath = "/subtitle") { url ->
                // NanoHTTPD обрабатывает запрос в своём собственном потоке —
                // viewModel.loadExternalSubtitle() и запись в Compose-state
                // (showCompanionQr) должны уйти на главный поток явно.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.loadExternalSubtitle(url)
                    showCompanionQr = false
                }
            }
            val port = server.startServer()
            val ip = LocalNetworkUtils.getLocalIpAddress()
            // "#subtitle" — подсказка телефону, какой экран показать после
            // сканирования (см. PhoneQrScanScreen.kt: разбирает суффикс
            // после "#", чтобы не заводить для этого отдельный QR-формат/JSON).
            companionAddress = if (ip != null) "http://$ip:$port#subtitle" else null
        } else {
            companionAddress = null
        }
        onDispose { server?.stop() }
    }

    val readyState = uiState as? PlayerUiState.Ready

    // Автоскрытие контроллера через 3с бездействия, но не пока открыто меню
    LaunchedEffect(lastInteraction, readyState?.showPlaybackMenu) {
        if (showControls && readyState?.showPlaybackMenu != true) {
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

                if (showCompanionQr) {
                    return@onKeyEvent when (event.key) {
                        Key.Back -> { showCompanionQr = false; true }
                        else -> false
                    }
                }

                if (ready.showPlaybackMenu) {
                    return@onKeyEvent when (event.key) {
                        Key.Back, Key.Menu -> { viewModel.dismissPlaybackMenu(); true }
                        else -> false // Up/Down/Center — отдаём PlaybackMenuOverlay
                    }
                }

                lastInteraction = System.currentTimeMillis()
                showControls = true
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { viewModel.togglePlayPause(); true }
                    // Раньше Left/Right всегда были перемоткой ±10с. При
                    // просмотре сериала (nextEpisodeId/previousEpisodeId
                    // заданы) те же клавиши переключают серию.
                    Key.DirectionRight -> {
                        if (ready.nextEpisodeId != null) {
                            seekToast = ready.nextEpisodeTitle?.let { "Следующая: $it" } ?: "Следующая серия"
                            viewModel.playNextEpisode()
                        } else {
                            seekToast = "+10 сек"
                            seekPulseNonce++
                            viewModel.seekForward()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (ready.previousEpisodeId != null) {
                            seekToast = ready.previousEpisodeTitle?.let { "Предыдущая: $it" } ?: "Предыдущая серия"
                            viewModel.playPreviousEpisode()
                        } else {
                            seekToast = "-10 сек"
                            seekPulseNonce++
                            viewModel.seekBackward()
                        }
                        true
                    }
                    // Key.Menu оставлен для пультов/клавиатур, где он есть, но
                    // у многих современных TV-пультов (например, штатный пульт
                    // Xiaomi TV Stick 4K) физической кнопки Menu нет вообще —
                    // без альтернативы панель качества/аудио/субтитров была
                    // недостижима. DirectionUp во время обычного воспроизведения
                    // ничем не занят — свободная клавиша.
                    Key.Menu, Key.DirectionUp -> { viewModel.togglePlaybackMenu(); true }
                    // "Подключить телефон" — DirectionDown, как и
                    // DirectionUp выше, ничем не занят во время обычного
                    // воспроизведения.
                    Key.DirectionDown -> { showCompanionQr = true; true }
                    Key.Back -> { onBackPressed(); true }
                    else -> false // любая другая кнопка — контроллер уже показан выше
                }
            }
    ) {
        // PlayerView через TextureView (не SurfaceView) — см.
        // res/layout/player_view_texture.xml, обходит частичное
        // перекрытие Compose-контента на слабых TV-чипах.
        AndroidView(
            factory = { ctx ->
                android.view.LayoutInflater.from(ctx)
                    .inflate(com.platinum.ott.R.layout.player_view_texture, null) as PlayerView
            },
            update = { it.player = viewModel.exoPlayer; it.keepScreenOn = true },
            modifier = Modifier.fillMaxSize()
        )
        when (val state = uiState) {
            is PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), Alignment.Center) { Text("Подготовка...", color = Color.White) }
            is PlayerUiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                        Button(onClick = { onBackPressed() }) { Text("Назад") }
                        Button(onClick = { viewModel.loadMovie(movieId) }) { Text("Повторить") }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                PlayerController(
                    isVisible = showControls,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = viewModel.exoPlayer.duration.coerceAtLeast(0L),
                    onTogglePlay = { viewModel.togglePlayPause() },
                    title = state.title,
                    hasNextEpisode = state.nextEpisodeId != null,
                    hasPreviousEpisode = state.previousEpisodeId != null,
                    onNextEpisode = { viewModel.playNextEpisode() },
                    onPreviousEpisode = { viewModel.playPreviousEpisode() },
                    onConnectPhone = { showCompanionQr = true },
                    isSeekActive = isDpadSeekActive,
                    subtitlesEnabled = state.subtitlesEnabled,
                    playbackSpeed = state.playbackSpeed,
                    onOpenMenuTab = { viewModel.openPlaybackMenu(it) },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.showPlaybackMenu) {
                    PlaybackMenuOverlay(
                        tab = state.menuTab,
                        onTabChange = { viewModel.setMenuTab(it) },
                        variants = state.variants,
                        currentVariant = state.currentVariant,
                        onSelectVariant = { viewModel.selectQuality(it) },
                        audioTracks = state.audioTracks,
                        onSelectAudio = { viewModel.selectAudioTrack(it) },
                        subtitleTracks = state.subtitleTracks,
                        subtitlesEnabled = state.subtitlesEnabled,
                        onSelectSubtitle = { viewModel.selectSubtitleTrack(it) },
                        onDisableSubtitles = { viewModel.disableSubtitles() },
                        onRequestExternalSubtitleQr = { viewModel.dismissPlaybackMenu(); showCompanionQr = true },
                        playbackSpeed = state.playbackSpeed,
                        onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                        onDismiss = { viewModel.dismissPlaybackMenu() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showCompanionQr) {
                    QrScanScreen(
                        content = companionAddress,
                        onDismiss = { showCompanionQr = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Короткая надпись по центру экрана на DirectionLeft/DirectionRight
        AnimatedVisibility(
            visible = seekToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZenithSurface.copy(alpha = 0.85f))
                    .padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingSM)
            ) {
                Text(text = seekToast ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
