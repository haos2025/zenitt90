package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.platinum.ott.core.subtitles.AutoSubtitleState
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.ui.theme.ZenithDurationMedium
import com.platinum.ott.ui.theme.ZenithEasingStandard
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithShapeSmall
import com.platinum.ott.ui.theme.ZenithSurface
import androidx.tv.material3.*
import kotlinx.coroutines.delay

/**
 * Раньше useController=false и пустая ветка Ready (\"/* Quality button,
 * controls */\") — видео проигрывалось совсем без управления. Теперь:
 *  - PlayerController (пауза/прогресс-бар/иконки настроек) — виден
 *    постоянно, пока экран в состоянии Ready (см. четвёртый раунд ниже —
 *    автоскрытие через 3с убрано).
 *  - PlaybackMenuOverlay (качество/аудио/субтитры/скорость) — открывается
 *    фокусируемыми иконками самой капсулы (MenuIconButton в
 *    PlayerController.kt, реальный tv-material3 Surface) или клавишей
 *    Menu на пультах/клавиатурах, где она есть; пока открыт, ключевые
 *    события Up/Down/Center НЕ перехватываются здесь, чтобы его
 *    собственный LazyColumn нормально работал через встроенную
 *    фокус-навигацию Compose.
 *  - D-pad Center/OK и системная Play/Pause с пульта — пауза/воспроизведение.
 *  - Left/Right — перемотка на 10 секунд (или переключение серии, если
 *    сериал) — сопровождается короткой надписью по центру экрана
 *    (seekToast), которая ненадолго появляется и гаснет.
 *  - Up/Down — НЕ перехватываются здесь (см. четвёртый раунд ниже),
 *    штатная фокус-навигация Compose сама переводит фокус на реальные
 *    фокусируемые элементы капсулы (иконки субтитров/аудио/качества/
 *    скорости внизу, иконка "Подключить телефон" наверху).
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
 * использовался для seekToast/showControls (авто-скрытие которого убрано
 * в четвёртом раунде, см. ниже).
 *
 * Четвёртый раунд (обратная связь с реального теста на пульте):
 *  - DirectionUp/DirectionDown раньше жёстко перехватывались здесь на
 *    "открыть меню оверлея"/"открыть телефон-компаньон" — это КОНФЛИКТОВАЛО
 *    с обычной фокус-навигацией Compose: MenuIconButton-иконки внизу
 *    капсулы и так были реальными tv-material3 Surface (задумывались
 *    фокусируемыми — см. комментарий в PlayerController.kt), но фокус на
 *    них в принципе не мог попасть с пульта, т.к. Up/Down ни разу не
 *    доходили до штатного focus-move — их всегда съедал этот обработчик
 *    первым. Both branches убраны; Up/Down больше не возвращают true
 *    здесь, событие проваливается в стандартную обработку Compose,
 *    которая сама переводит фокус на ближайший фокусируемый элемент в
 *    нужном направлении (вниз — к иконкам субтитров/аудио/качества/
 *    скорости, вверх — к "Подключить телефон", см. PlayerController.kt).
 *    Key.Menu оставлен как есть — это отдельная физическая клавиша, не
 *    D-pad, у пультов/клавиатур, где она есть, ничего не меняется.
 *  - Автоскрытие капсулы через 3с бездействия убрано целиком (было:
 *    LaunchedEffect(lastInteraction, showPlaybackMenu) { delay(3000);
 *    showControls = false }) — реальный репорт с теста: во время
 *    удержания перемотки (DirectionLeft/Right) капсула всё равно иногда
 *    гасла, обрывая обратную связь по прогресс-бару прямо в процессе.
 *    Капсула теперь видна постоянно, пока PlayerUiState.Ready — весь
 *    showControls/lastInteraction как отдельное состояние с этим убран,
 *    PlayerController получает isVisible = true константой.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    movieId: String,
    onBackPressed: () -> Unit,
    preferredVariantUrl: String? = null,
    catchupStartMillis: Long? = null,
    catchupEndMillis: Long? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(movieId) { viewModel.loadMovie(movieId, preferredVariantUrl, catchupStartMillis, catchupEndMillis) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    // PROMPT_SUBTITLES.md, подзадача 8.
    val autoSubtitleState by viewModel.autoSubtitleState.collectAsStateWithLifecycle()
    val autoSubtitleCues by viewModel.autoSubtitleCues.collectAsStateWithLifecycle()

    var currentPositionMs by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Кнопки перемотки/эпизодов в капсуле — decorative (см.
    // PlayerController.kt: не получают фокус пульта напрямую, реальное
    // действие идёт через DirectionLeft/DirectionRight ниже). Короткая
    // полупрозрачная надпись по центру экрана ("10 сек" / название серии),
    // сама гаснет через 700ms — независимо от капсулы снизу, которая
    // (с четвёртого раунда) больше не автоскрывается вообще.
    var seekToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekToast) {
        if (seekToast != null) {
            delay(700)
            seekToast = null
        }
    }

    // PROMPT_EPG.md, подзадача 6 (заппинг по номерам) — только для живых
    // каналов (movieId с префиксом "ch_", проверяется и здесь, и ещё раз в
    // PlayerViewModel.zapToChannelNumber() — UI просто не должен копить
    // буфер там, где им всё равно некому воспользоваться). До 4 цифр — при
    // достижении лимита переход происходит сразу, не дожидаясь паузы в
    // вводе; иначе после ZAP_INPUT_TIMEOUT_MS с момента последней цифры.
    // LaunchedEffect(zapBuffer) перезапускается на каждую новую цифру — тот
    // же паттерн, что и у seekToast/seekPulseNonce рядом.
    var zapBuffer by remember { mutableStateOf("") }
    LaunchedEffect(zapBuffer) {
        if (zapBuffer.isEmpty()) return@LaunchedEffect
        if (zapBuffer.length < ZAP_MAX_DIGITS) delay(ZAP_INPUT_TIMEOUT_MS)
        zapBuffer.toIntOrNull()?.let { viewModel.zapToChannelNumber(it) }
        zapBuffer = ""
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

    // У ExoPlayer нет готового Flow под текущую позицию — опрашиваем, пока экран Ready
    LaunchedEffect(uiState) {
        while (uiState is PlayerUiState.Ready) {
            currentPositionMs = viewModel.exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Раньше Center/Left/Right обрабатывались здесь безусловно — даже когда
    // (после четвёртого раунда) фокус реально стоял на настоящей
    // фокусируемой иконке капсулы (MenuIconButton), нажатие стрелки туда
    // не долетало: сначала выяснилось, что декоративные кнопки
    // (IconGlyphButton — play/pause и т.д.) на самом деле ловили фокус на
    // себя раньше настоящих иконок (см. фикс в PlayerController.kt), а
    // даже после того, как это исправлено, Left/Right, не поглощённые
    // сфокусированной иконкой (у неё нет обработки стрелок, только OK),
    // всё равно поднимались по дереву сюда и безусловно перематывали —
    // реальный репорт с пульта: "стрелки всегда только перематывают, до
    // других кнопок не добраться". rootHasFocus ниже — фокус именно на
    // этом Box (не на потомке): пока он true, стрелки/OK работают как
    // глобальные хоткеи перемотки/паузы; как только фокус ушёл на
    // конкретную кнопку капсулы, эти клавиши здесь больше не
    // перехватываются — событие просто не доходит досюда (сфокусированный
    // элемент либо сам его обработал, либо это стрелка для обычной
    // фокус-навигации Compose между иконками).
    var rootHasFocus by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { rootHasFocus = it.isFocused }
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

                // Back — стандартное TV-поведение "выйти на один уровень":
                // если фокус сейчас на конкретной кнопке капсулы (не на
                // видео), первый Back возвращает фокус на видео (снова
                // включает хоткеи перемотки/паузы), а не сразу закрывает
                // плеер — иначе не было бы способа "выйти из режима
                // настроек капсулы", кроме как случайно нажать что-то ещё.
                // Следующий Back (когда фокус уже на видео) выходит из
                // плеера как раньше.
                if (event.key == Key.Back) {
                    if (rootHasFocus) onBackPressed() else focusRequester.requestFocus()
                    return@onKeyEvent true
                }
                if (event.key == Key.Menu) { viewModel.togglePlaybackMenu(); return@onKeyEvent true }
                if (!rootHasFocus) return@onKeyEvent false

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
                    // Key.Menu и Key.Back обработаны выше безусловно (см.
                    // комментарии там) — сюда доходят, только если
                    // rootHasFocus, для остальных необработанных клавиш
                    // ничего не перехватываем.
                    else -> {
                        val digit = digitFromKey(event.key)
                        if (digit != null && movieId.startsWith("ch_")) {
                            zapBuffer = (zapBuffer + digit).takeLast(ZAP_MAX_DIGITS)
                            true
                        } else false
                    }
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
                        Button(onClick = { viewModel.loadMovie(movieId, catchupStartMillis = catchupStartMillis, catchupEndMillis = catchupEndMillis) }) { Text("Повторить") }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                PlayerController(
                    // Раньше — showControls, гасший через 3с бездействия
                    // (см. комментарий вверху файла, четвёртый раунд). Теперь
                    // капсула видна постоянно, пока экран в Ready.
                    isVisible = true,
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
                        autoSubtitleState = autoSubtitleState,
                        onToggleAutoSubtitles = {
                            if (autoSubtitleState == AutoSubtitleState.Off) viewModel.enableAutoSubtitles()
                            else viewModel.disableAutoSubtitles()
                        },
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

        // PROMPT_SUBTITLES.md, подзадача 8 — AI-сгенерированные реплики
        // (оркестратор, подзадача 6) рендерятся отдельным текстовым слоем,
        // НЕ через ExoPlayer/SubtitleView: тот показывает только статичную
        // дорожку, известную заранее (встроенную или загруженную целиком
        // через loadExternalSubtitle — в т.ч. найденную OpenSubtitles, см.
        // подзадачу 1) — AI-путь копит реплики прогрессивно, список
        // растёт по ходу просмотра, готового файла для ExoPlayer нет.
        // Пока используется OpenSubtitles (autoSubtitleCues пуст всегда в
        // этом случае — оркестратор туда ничего не пишет), это условие
        // просто не сработает — двойного рендера с родной дорожкой не будет.
        val activeAutoSubtitleText = remember(autoSubtitleCues, currentPositionMs) {
            autoSubtitleCues.firstOrNull { currentPositionMs in it.startMs..it.endMs }?.text
        }
        if (activeAutoSubtitleText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = 120.dp)
                    .clip(ZenithShapeSmall)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
            ) {
                Text(
                    text = activeAutoSubtitleText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Короткая надпись по центру экрана на DirectionLeft/DirectionRight
        // Motion.kt (PROMPT_DESIGN_SYSTEM.md, подзадача 2) — та же
        // осознанная замена дефолтного FastOutSlowInEasing на единую
        // ZenithEasingStandard, что и в PlayerController.kt/
        // PhonePlayerController.kt.
        AnimatedVisibility(
            visible = seekToast != null,
            enter = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            exit = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(ZenithShapeMedium)
                    .background(ZenithSurface.copy(alpha = 0.85f))
                    .padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingSM)
            ) {
                Text(text = seekToast ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
        // PROMPT_EPG.md, подзадача 6 — набираемый номер канала, тот же
        // паттерн AnimatedVisibility, что и у seekToast выше.
        AnimatedVisibility(
            visible = zapBuffer.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            exit = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(ZenithDimens.paddingL)
                    .clip(ZenithShapeMedium)
                    .background(ZenithSurface.copy(alpha = 0.85f))
                    .padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingSM)
            ) {
                Text(text = "Канал: $zapBuffer", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

// PROMPT_EPG.md, подзадача 6 — до 4 цифр (каналов больше 9999 у типичного
// провайдера не бывает), 1200мс — тот же порядок времени, что обычно
// используют TV-приставки для ввода номера канала на пульте.
private const val ZAP_MAX_DIGITS = 4
private const val ZAP_INPUT_TIMEOUT_MS = 1200L

// И основной цифровой ряд, и NumPad — разные пульты/эмуляторы шлют разные
// коды для "цифра 5", не полагаемся на то, что все пришлют один и тот же.
private fun digitFromKey(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}
