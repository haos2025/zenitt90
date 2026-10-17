package com.platinum.ott.presentation.screens.player

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.platinum.ott.core.LiveArrowMode
import com.platinum.ott.core.companion.CompanionHttpServer
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.presentation.components.PlayerProgressStrip
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
 * Плеер ТВ. Оверлей управления — двухуровневый (модель Б), содержимое зависит от
 * типа воспроизведения (PlayerKind: фильм / сериал / эфир / запись):
 *
 *  Фокус Compose всегда на корне экрана; подсвеченная кнопка оверлея — selectedAction
 *  («виртуальный фокус», см. PlayerController.kt).
 *  Управление на видео (selectedAction == null): Влево/Вправо — перемотка ±10 с (в эфире
 *    не заняты), OK — пауза, Вниз — на ряд кнопок, Вверх — скрыть оверлей (в эфире Вверх
 *    открывает список каналов).
 *  Ряд кнопок: Влево/Вправо — выбор кнопки, OK — нажать, Вверх — назад на видео.
 *  Назад (BackHandler) — на ступень выше: закрыть модальное окно/меню → с кнопок на видео →
 *    скрыть оверлей → выйти из плеера. Скрытый оверлей показывается любой стрелкой/OK.
 *  Модальные окна (PlayerModals.kt): серии, каналы, программа канала; закрываются Назад.
 *  Menu — меню качества/звука/субтитров/скорости. CH+/CH− и цифры — каналы (эфир),
 *  медиа-клавиши работают всегда. Кнопки «Назад» на экране нет — только системная клавиша.
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
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    // «Виртуальный фокус» оверлея: null — управление на видео (Влево/Вправо = перемотка),
    // иначе — подсвеченная кнопка ряда. Настоящий фокус Compose всегда остаётся на корне.
    var selectedAction by remember { mutableStateOf<OverlayAction?>(null) }
    val retryFocusRequester = remember { FocusRequester() }

    // Модальные окна оверлея и их данные (null — ещё грузится).
    var modal by remember { mutableStateOf(PlayerModal.NONE) }
    var episodes by remember { mutableStateOf<List<com.platinum.ott.domain.model.Movie>?>(null) }
    var channelsList by remember { mutableStateOf<List<com.platinum.ott.data.repository.ChannelUiItem>?>(null) }
    var programChannel by remember { mutableStateOf<com.platinum.ott.data.repository.ChannelUiItem?>(null) }
    var programs by remember { mutableStateOf<List<com.platinum.ott.data.local.entity.EpgProgramEntity>?>(null) }
    // «Сейчас / далее» для постоянной шапки оверлея на живом канале.
    var liveInfo by remember { mutableStateOf<ZapBannerInfo?>(null) }

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
    // РЕДИЗАЙН (заппинг стрелками вверх/вниз + мини-EPG баннер) — отдельно
    // от zapBuffer выше: тот показывает набираемые ЦИФРЫ (до переключения),
    // этот — результат уже СОСТОЯВШЕГОСЯ переключения (стрелками или по
    // номеру), с названием канала и текущей программой из EPG.
    val zapBannerInfo by viewModel.zapBanner.collectAsStateWithLifecycle()
    val audioIssue by viewModel.audioIssue.collectAsStateWithLifecycle()

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

    // Оверлей: автоскрытие капсулы (настройка "Автоскрытие управления
    // плеера"). interactionNonce растёт на любое нажатие клавиши (см.
    // onPreviewKeyEvent ниже) — перезапускает таймер. Таймер НЕ тикает, пока
    // клавиша перемотки физически зажата (isDpadSeekActive), открыто меню
    // воспроизведения или QR телефона-компаньона — иначе капсула гасла бы
    // посреди удержания перемотки (см. комментарий вверху файла, 4-й раунд).
    val overlaySettings = remember { viewModel.overlaySettings }
    val hideDelayMs = overlaySettings.hideDelayMs ?: 0  // TV: по умолчанию не скрывать
    // У live-каналов буфер — единицы секунд, подложка не несёт смысла.
    val showBuffered = overlaySettings.showBuffered && !movieId.startsWith("ch_")
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionNonce by remember { mutableStateOf(0) }

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
            bufferedPositionMs = viewModel.exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val menuOpen = (uiState as? PlayerUiState.Ready)?.showPlaybackMenu == true
    val holdForPause = overlaySettings.keepControlsWhenPaused && !isPlaying
    val readyState = uiState as? PlayerUiState.Ready
    val isLiveKind = movieId.startsWith("ch_") && readyState != null && !readyState.isCatchup
    val modalOpen = modal != PlayerModal.NONE

    // Автоскрытие (если включено в настройках). Само «показать» делает touchControls() в
    // обработчике клавиш — этот эффект ничего не показывает насильно, поэтому ручное
    // «Вверх = скрыть» не перебивается.
    LaunchedEffect(hideDelayMs, interactionNonce, isDpadSeekActive, menuOpen, showCompanionQr, holdForPause, modalOpen, controlsVisible, uiState is PlayerUiState.Ready) {
        if (!controlsVisible || hideDelayMs <= 0 || uiState !is PlayerUiState.Ready) return@LaunchedEffect
        if (isDpadSeekActive || menuOpen || showCompanionQr || holdForPause || modalOpen) return@LaunchedEffect
        delay(hideDelayMs.toLong())
        controlsVisible = false
        selectedAction = null
        focusRequester.safeRequestFocus()
    }

    // «Сейчас / далее» на живом канале: обновляем при смене канала и раз в 30 секунд.
    LaunchedEffect(isLiveKind, readyState?.title) {
        liveInfo = null
        while (isLiveKind) {
            liveInfo = viewModel.getNowNext()
            delay(30_000)
        }
    }

    // Данные модальных окон грузим при открытии.
    LaunchedEffect(modal) {
        when (modal) {
            PlayerModal.EPISODES -> {
                episodes = null
                val sid = readyState?.seriesId
                episodes = if (sid != null) viewModel.getEpisodeList(sid) else emptyList()
            }
            PlayerModal.CHANNELS -> {
                channelsList = null
                channelsList = viewModel.getSubscribedChannels()
            }
            PlayerModal.PROGRAM -> {
                programs = null
                val ch = viewModel.getCurrentChannel()
                programChannel = ch
                val now = System.currentTimeMillis()
                val archiveMs = (ch?.catchupDaysAvailable ?: 0).coerceAtLeast(1) * 86_400_000L
                programs = if (ch != null) viewModel.getChannelProgram(ch.id, now - archiveMs, now + 2 * 86_400_000L) else emptyList()
            }
            PlayerModal.NONE -> {}
        }
    }
    // Корень экрана должен держать фокус всегда, когда нет модального окна/меню/QR —
    // иначе клавиши пульта не дойдут до обработчика ниже.
    val isReady = uiState is PlayerUiState.Ready
    LaunchedEffect(modalOpen, menuOpen, showCompanionQr, isReady) {
        if (isReady && !modalOpen && !menuOpen && !showCompanionQr) {
            delay(30)
            focusRequester.safeRequestFocus()
        }
    }
    // В экране ошибки фокус на «Повторить», чтобы OK сразу работал.
    LaunchedEffect(uiState is PlayerUiState.Error) {
        if (uiState is PlayerUiState.Error) { delay(50); retryFocusRequester.safeRequestFocus() }
    }

    // ВИРТУАЛЬНЫЙ ФОКУС: кнопки оверлея не фокусируемые, настоящий фокус всегда на этом корне,
    // а какая кнопка подсвечена — хранит selectedAction. Так фокус нельзя «потерять» при
    // скрытии оверлея/закрытии модалки (из-за этого раньше переставали работать Вниз и Назад).
    fun touchControls() { controlsVisible = true; interactionNonce++ }
    fun hideControls() { controlsVisible = false; selectedAction = null }

    // Назад — ступенями: QR/модалка/меню → кнопки → видео → скрыть оверлей → выйти.
    // BackHandler надёжнее, чем перехват Key.Back: работает и с жестом/системной кнопкой.
    BackHandler(enabled = true) {
        // «Назад» в окне оверлея (каналы/серии/программа/меню настроек/QR): по умолчанию закрывает окно И
        // оверлей одним нажатием (возврат к видео). Режим «По шагам» (Настройки → Управление пультом) —
        // как раньше: окно → выделение кнопки → оверлей → выход.
        val closeAll = overlaySettings.backClosesAll
        // Диагностика двойного «Назад»: в logcat фильтр ZenithBack покажет, что именно было открыто в момент нажатия.
        android.util.Log.d("ZenithBack", "back: modal=$modal menu=$menuOpen qr=$showCompanionQr sel=$selectedAction controls=$controlsVisible closeAll=$closeAll")
        fun afterWindowClosed() { selectedAction = null; if (closeAll) controlsVisible = false else touchControls() }
        when {
            uiState !is PlayerUiState.Ready -> onBackPressed()
            showCompanionQr -> { showCompanionQr = false; afterWindowClosed() }
            modal != PlayerModal.NONE -> { modal = PlayerModal.NONE; afterWindowClosed() }
            menuOpen -> { viewModel.dismissPlaybackMenu(); afterWindowClosed() }
            selectedAction != null -> { selectedAction = null; touchControls() }
            controlsVisible -> hideControls()
            else -> onBackPressed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) interactionNonce++
                false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val ready = uiState as? PlayerUiState.Ready ?: return@onKeyEvent false
                val isChannel = movieId.startsWith("ch_")

                // Поверх экрана модальное окно / QR / меню — клавиши отдаём им (Назад — в BackHandler).
                if (showCompanionQr || modal != PlayerModal.NONE) return@onKeyEvent false
                if (ready.showPlaybackMenu) {
                    return@onKeyEvent if (event.key == Key.Menu) { viewModel.dismissPlaybackMenu(); true } else false
                }
                if (event.key == Key.Menu) { viewModel.togglePlaybackMenu(); return@onKeyEvent true }

                // Медиа-клавиши и CH+/CH− — всегда.
                when (event.key) {
                    Key.MediaPlay -> { viewModel.exoPlayer.play(); return@onKeyEvent true }
                    Key.MediaPause -> { viewModel.exoPlayer.pause(); return@onKeyEvent true }
                    Key.MediaPlayPause -> { touchControls(); viewModel.togglePlayPause(); return@onKeyEvent true }
                    Key.MediaFastForward -> { if (isChannel && !ready.isCatchup) return@onKeyEvent false; viewModel.seekForward(); seekToast = "+10 сек"; return@onKeyEvent true }
                    Key.MediaRewind -> { if (isChannel && !ready.isCatchup) return@onKeyEvent false; viewModel.seekBackward(); seekToast = "-10 сек"; return@onKeyEvent true }
                    Key.ChannelUp -> { if (isChannel) { viewModel.zapToNextChannel(); touchControls(); return@onKeyEvent true } else return@onKeyEvent false }
                    Key.ChannelDown -> { if (isChannel) { viewModel.zapToPreviousChannel(); touchControls(); return@onKeyEvent true } else return@onKeyEvent false }
                    else -> {}
                }

                val kind = playerKindOf(movieId, ready)
                val actions = overlayActions(kind, ready.previousEpisodeId != null, ready.nextEpisodeId != null)
                val canSeek = !isChannel || ready.isCatchup
                val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter

                // Оверлей скрыт: любая стрелка/OK сначала его показывает (OK ещё и ставит паузу/play,
                // Влево/Вправо — перематывают, в эфире Вверх сразу открывает «Каналы»).
                val sel = selectedAction
                if (sel != null && controlsVisible) {
                    // Кнопки ряда.
                    val idx = actions.indexOf(sel).let { if (it < 0) actions.indexOf(OverlayAction.PLAY).coerceAtLeast(0) else it }
                    return@onKeyEvent when {
                        event.key == Key.DirectionRight -> { selectedAction = actions[(idx + 1).coerceAtMost(actions.lastIndex)]; touchControls(); true }
                        event.key == Key.DirectionLeft -> { selectedAction = actions[(idx - 1).coerceAtLeast(0)]; touchControls(); true }
                        event.key == Key.DirectionUp -> { selectedAction = null; touchControls(); true }
                        event.key == Key.DirectionDown -> { touchControls(); true }
                        isCenter -> {
                            touchControls()
                            when (actions[idx]) {
                                OverlayAction.PLAY -> viewModel.togglePlayPause()
                                OverlayAction.PREV -> viewModel.playPreviousEpisode()
                                OverlayAction.NEXT -> viewModel.playNextEpisode()
                                OverlayAction.EPISODES -> modal = PlayerModal.EPISODES
                                OverlayAction.CHANNELS -> modal = PlayerModal.CHANNELS
                                OverlayAction.PROGRAM -> modal = PlayerModal.PROGRAM
                                OverlayAction.LIVE -> viewModel.playLive()
                                OverlayAction.SUBTITLES -> viewModel.openPlaybackMenu(PlaybackMenuTab.SUBTITLES)
                                OverlayAction.AUDIO -> viewModel.openPlaybackMenu(PlaybackMenuTab.AUDIO)
                                OverlayAction.QUALITY -> viewModel.openPlaybackMenu(PlaybackMenuTab.QUALITY)
                                OverlayAction.SPEED -> viewModel.openPlaybackMenu(PlaybackMenuTab.SPEED)
                            }
                            true
                        }
                        else -> false
                    }
                }

                // Управление «на видео».
                // В ПРЯМОМ эфире поведение стрелок задаёт настройка «Стрелки в эфире»
                // (LiveArrowMode, Настройки → Управление пультом); в фильмах, сериалах
                // и архиве стрелки всегда перематывают (см. ниже).
                val liveNow = isChannel && !ready.isCatchup
                val arrowMode = overlaySettings.liveArrowMode
                val firstRepeat = event.nativeKeyEvent.repeatCount == 0 // повторы зажатой клавиши не переключают каналы
                when {
                    // UP_DOWN_ZAP: Вверх/Вниз заняты каналами, поэтому OK показывает оверлей и входит в его кнопки.
                    liveNow && arrowMode == LiveArrowMode.UP_DOWN_ZAP && isCenter -> {
                        if (!controlsVisible) touchControls()
                        else { selectedAction = actions.firstOrNull { it == OverlayAction.CHANNELS } ?: actions.first(); touchControls() }
                        true
                    }
                    isCenter -> { touchControls(); viewModel.togglePlayPause(); true }
                    // LEFT_RIGHT_ZAP: Вправо/Влево — следующий/предыдущий канал.
                    liveNow && arrowMode == LiveArrowMode.LEFT_RIGHT_ZAP && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) -> {
                        if (firstRepeat) {
                            if (event.key == Key.DirectionRight) viewModel.zapToNextChannel() else viewModel.zapToPreviousChannel()
                        }
                        true
                    }
                    // UP_DOWN_ZAP: Вверх/Вниз — каналы, Влево — список каналов, Вправо — программа.
                    liveNow && arrowMode == LiveArrowMode.UP_DOWN_ZAP && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                        if (firstRepeat) {
                            if (event.key == Key.DirectionUp) viewModel.zapToNextChannel() else viewModel.zapToPreviousChannel()
                        }
                        true
                    }
                    liveNow && arrowMode == LiveArrowMode.UP_DOWN_ZAP && event.key == Key.DirectionLeft -> { modal = PlayerModal.CHANNELS; true }
                    liveNow && arrowMode == LiveArrowMode.UP_DOWN_ZAP && event.key == Key.DirectionRight -> { modal = PlayerModal.PROGRAM; true }
                    // CLASSIC: Влево/Вправо в эфире ничего не делают (только показывают оверлей).
                    liveNow && arrowMode == LiveArrowMode.CLASSIC && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) -> { touchControls(); true }
                    // CLASSIC: Вверх в эфире — список каналов (как раньше).
                    liveNow && arrowMode == LiveArrowMode.CLASSIC && event.key == Key.DirectionUp -> { modal = PlayerModal.CHANNELS; true }
                    event.key == Key.DirectionRight -> {
                        touchControls()
                        if (canSeek) { seekToast = "+10 сек"; seekPulseNonce++; viewModel.seekForward() }
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        touchControls()
                        if (canSeek) { seekToast = "-10 сек"; seekPulseNonce++; viewModel.seekBackward() }
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        if (!controlsVisible) touchControls()
                        else { selectedAction = OverlayAction.PLAY; touchControls() }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        when {
                            !controlsVisible -> touchControls()
                            else -> hideControls()
                        }
                        true
                    }
                    else -> {
                        val digit = digitFromKey(event.key)
                        if (digit != null && isChannel) {
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
            update = {
                it.player = viewModel.exoPlayer
                it.keepScreenOn = true
                // Системный View не должен забирать фокус пульта у корня Compose.
                it.isFocusable = false
                it.isFocusableInTouchMode = false
                it.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            },
            modifier = Modifier.fillMaxSize()
        )
        when (val state = uiState) {
            is PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), Alignment.Center) { Text("Подготовка...", color = ZenithOnSurface) }
            is PlayerUiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                        Button(onClick = { viewModel.loadMovie(movieId, catchupStartMillis = catchupStartMillis, catchupEndMillis = catchupEndMillis) }, modifier = Modifier.focusRequester(retryFocusRequester)) { Text("Повторить") }
                    }
                }
            }
            is PlayerUiState.Ready -> {
                PlayerController(
                    isVisible = controlsVisible,
                    selected = selectedAction,
                    kind = playerKindOf(movieId, state),
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = viewModel.exoPlayer.duration.coerceAtLeast(0L),
                    title = state.title,
                    hasNextEpisode = state.nextEpisodeId != null,
                    hasPreviousEpisode = state.previousEpisodeId != null,
                    liveInfo = liveInfo,
                    isSeekActive = isDpadSeekActive,
                    bufferedPositionMs = if (showBuffered) bufferedPositionMs else 0L,
                    subtitlesEnabled = state.subtitlesEnabled,
                    playbackSpeed = state.playbackSpeed,
                    modifier = Modifier.fillMaxSize()
                )
                // Тонкая полоска прогресса — видна только пока капсула спрятана
                // (при постоянной капсуле у неё есть собственный прогресс-бар).
                if (!controlsVisible && overlaySettings.showProgressStrip) {
                    PlayerProgressStrip(
                        positionMs = currentPositionMs,
                        durationMs = viewModel.exoPlayer.duration.coerceAtLeast(0L),
                        thickness = overlaySettings.progressStripDp.dp,
                        color = MaterialTheme.colorScheme.primary,
                        bufferedMs = if (showBuffered) bufferedPositionMs else 0L,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                when (modal) {
                    PlayerModal.EPISODES -> EpisodesModal(
                        seriesTitle = state.title, episodes = episodes, currentId = viewModel.currentId,
                        onPick = { m -> modal = PlayerModal.NONE; selectedAction = null; viewModel.playEpisode(m.id) }
                    )
                    PlayerModal.CHANNELS -> ChannelsModal(
                        channels = channelsList, currentId = viewModel.currentId,
                        onPick = { c -> modal = PlayerModal.NONE; selectedAction = null; viewModel.switchToChannel(c) }
                    )
                    PlayerModal.PROGRAM -> ProgramModal(
                        channel = programChannel, programs = programs, nowMs = System.currentTimeMillis(),
                        watchingCatchup = state.isCatchup,
                        onWatchLive = { modal = PlayerModal.NONE; viewModel.playLive() },
                        onWatchCatchup = { startMs, endMs -> modal = PlayerModal.NONE; viewModel.playCatchup(startMs, endMs) }
                    )
                    PlayerModal.NONE -> {}
                }
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
                    color = ZenithOnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Нет поддерживаемой аудиодорожки — объясняем, почему тишина.
        if (audioIssue != null && uiState is PlayerUiState.Ready) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = ZenithDimens.paddingL)
                    .clip(ZenithShapeMedium)
                    .background(ZenithSurface.copy(alpha = 0.9f))
                    .padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingSM)
            ) {
                Text(text = audioIssue ?: "", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface)
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
                Text(text = seekToast ?: "", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
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
                Text(text = "Канал: $zapBuffer", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
            }
        }
        // РЕДИЗАЙН (заппинг) — сам баннер переключения, с логотипом/номером,
        // текущей программой и её прогрессом (по времени ПРОГРАММЫ, не
        // видео), и следующей программой, если EPG для канала есть.
        AnimatedVisibility(
            visible = zapBannerInfo != null,
            enter = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            exit = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            zapBannerInfo?.let { info ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(ZenithDimens.paddingL)
                        .width(380.dp)
                        .clip(ZenithShapeMedium)
                        .background(ZenithSurface.copy(alpha = 0.92f))
                        .padding(ZenithDimens.paddingM)
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (info.channelLogo != null) {
                            AsyncImage(
                                model = info.channelLogo, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.75f)
                            )
                        } else {
                            Text("${info.channelNumber}", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
                        }
                    }
                    Spacer(Modifier.width(ZenithDimens.paddingM))
                    Column(Modifier.weight(1f)) {
                        Text(info.channelName, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (info.currentProgramTitle != null) {
                            Text(info.currentProgramTitle, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info.currentProgramProgress != null) {
                                LinearProgressIndicator(
                                    progress = { info.currentProgramProgress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp)
                                )
                            }
                            if (info.nextProgramTitle != null) {
                                Text(
                                    "Далее: ${info.nextProgramTitle}",
                                    style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text("Нет данных о программе передач", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
                        }
                    }
                }
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

/** Тип воспроизводимого — по маршруту (ch_…) и состоянию плеера. */
private fun playerKindOf(movieId: String, state: PlayerUiState.Ready): PlayerKind = when {
    movieId.startsWith("ch_") -> if (state.isCatchup) PlayerKind.CATCHUP else PlayerKind.LIVE
    state.seriesId != null -> PlayerKind.SERIES
    else -> PlayerKind.MOVIE
}
