package com.platinum.ott.presentation.phone.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.presentation.screens.player.PlaybackMenuTab
import com.platinum.ott.presentation.screens.player.TrackOption
import com.platinum.ott.ui.theme.ZenithSecondary
import com.platinum.ott.ui.theme.ZenithSurface

// Третий раунд редизайна: убраны кнопки ±10с (были рабочими, но
// дублировали жест свайпа по экрану — см. PhonePlayerScreen.kt,
// seekDeltaFromDrag — перемотка никуда не делась, просто больше не имеет
// отдельной видимой кнопки). Для обычного фильма транспортный кластер —
// один play/pause, на том же месте слева (не по центру всей капсулы),
// где он стоял и раньше между двумя кнопками перемотки. Для сериала
// след./пред. эпизода остаются — это отдельная функция, не перемотка.
//
// Fullscreen переехал из верхней строки (была отдельная кнопка рядом с
// заголовком) в общий ряд иконок капсулы — теперь вообще все элементы
// управления в одной плашке снизу, верхняя строка — только "Назад +
// Название".
//
// Гибридный компакт: вместо четырёх отдельных иконок настроек
// (субтитры/аудио/качество/скорость) в ряду капсулы остаются только
// субтитры и fullscreen — самые частые действия. Аудио/качество/скорость
// уходят под одну кнопку "⋮", открывающую модальное окно снизу
// (ModalBottomSheet) с этими тремя категориями. Один и тот же макет и в
// портрете, и в landscape/fullscreen — специально не заводили вторую,
// параллельную раскладку под ориентацию.
//
// Прогресс-бар (ScrubberBar ниже) — раньше был полностью самописный
// pointerInput с ДВУМЯ параллельно запущенными детекторами
// (detectTapGestures + detectDragGestures на одном и том же потоке
// событий). На тапе это было безобидно, но на драге (то есть на самом
// частом сценарии — перетаскивании) оба детектора реагировали на одно и
// то же отпускание пальца: onSeekTo мог быть вызван дважды с РАЗНЫМИ
// значениями (гонка, кто из двух корутин отработает последним), а
// возврат tryAwaitRelease() нигде не проверялся — если драг успевал
// consume() событие раньше, tap-ветка всё равно безусловно продолжала
// свой tryAwaitRelease→onSeekTo(стартовая_позиция), из-за чего прямо
// посреди активного перетаскивания трек мог на кадр схлопнуться обратно
// (isScrubbing=false) и превью времени дёрнуться назад к текущей позиции
// воспроизведения. Переписано на стандартный Material3 Slider
// (value/onValueChange/onValueChangeFinished) — жест теперь обрабатывает
// один-единственный, давно обкатанный код библиотеки, а не два
// конкурирующих между собой. Растущий трек (4dp→10dp, 150ms ease-out,
// тот же принцип, что и на TV — там по удержанию D-pad, см.
// PlayerController.kt) и минималистичный вид без видимого "ползунка"
// сохранены через кастомные track/thumb-слоты Slider (появились в
// material3 ещё до версии из текущего BOM — 1.3.0, доступны без
// дополнительных зависимостей, только @OptIn(ExperimentalMaterial3Api)).
// Заодно на баре появилась индикация буферизации (ExoPlayer.bufferedPosition
// был доступен и раньше, но нигде не читался) и градиент вместо сплошной
// заливки пройденной части. Время слито в один ряд со скраббером вместо
// отдельной строки под ним — экономит высоту капсулы.
@Composable
fun PhonePlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    title: String,
    currentPositionMs: Long,
    durationMs: Long,
    // ExoPlayer.bufferedPosition — существовал в проекте с самого начала,
    // но ScrubberBar о нём не знал: на баре было только "просмотрено/не
    // просмотрено", без промежуточного состояния "уже загружено вперёд".
    // Дефолт 0L — вызовы, которые ещё не прокинули значение явно (если
    // такие останутся), просто не покажут индикатор буферизации, не упадут.
    bufferedPositionMs: Long = 0L,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant?,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectVariant: (StreamVariant) -> Unit,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onLoadExternalSubtitle: (String) -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onBackPressed: () -> Unit,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    isFullscreen: Boolean = true,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuTab by remember { mutableStateOf<PlaybackMenuTab?>(null) }
    // Отдельно от menuTab (субтитры остаются собственной иконкой/диалогом,
    // как раньше) — аудио/качество/скорость теперь живут в одном
    // bottom sheet. moreSheetCategory == null — показывается список из
    // трёх категорий, не null — список значений внутри выбранной
    // категории (drill-down в том же листе, без стека диалогов).
    var moreSheetOpen by remember { mutableStateOf(false) }
    var moreSheetCategory by remember { mutableStateOf<PlaybackMenuTab?>(null) }
    val isSeries = hasNextEpisode || hasPreviousEpisode

    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.3f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))

            // Верхняя строка — только "Назад + Название", fullscreen
            // отсюда убран (переехал в капсулу).
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(ZenithSurface.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
                ) {
                    // Время слито в один ряд со скраббером (было: слайдер
                    // отдельной строкой, время отдельной строкой под ним).
                    var previewPositionMs by remember { mutableStateOf<Long?>(null) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMs(previewPositionMs ?: currentPositionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        ScrubberBar(
                            positionMs = currentPositionMs,
                            durationMs = durationMs,
                            bufferedPositionMs = bufferedPositionMs,
                            onPreview = { previewPositionMs = it },
                            onSeekTo = { onSeekTo(it); previewPositionMs = null },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        Text(formatMs(durationMs), color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                    }

                    // Один ряд: слева — транспортный кластер (play/pause,
                    // либо + след./пред. эпизод по бокам для сериала),
                    // справа — субтитры / "⋮" / fullscreen.
                    Row(
                        Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSeries) {
                                TransportButton(
                                    icon = Icons.Default.SkipPrevious,
                                    contentDescription = "Предыдущая серия",
                                    enabled = hasPreviousEpisode,
                                    onClick = onPreviousEpisode
                                )
                                Spacer(Modifier.width(ZenithDimens.paddingM))
                            }
                            TransportButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Play",
                                isPrimary = true,
                                onClick = onTogglePlay
                            )
                            if (isSeries) {
                                Spacer(Modifier.width(ZenithDimens.paddingM))
                                TransportButton(
                                    icon = Icons.Default.SkipNext,
                                    contentDescription = "Следующая серия",
                                    enabled = hasNextEpisode,
                                    onClick = onNextEpisode
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                            SmallMenuIconButton(icon = Icons.Default.ClosedCaption, contentDescription = "Субтитры", isActive = subtitlesEnabled) { menuTab = PlaybackMenuTab.SUBTITLES }
                            SmallMenuIconButton(icon = Icons.Default.MoreVert, contentDescription = "Ещё настройки", isActive = false) { moreSheetOpen = true; moreSheetCategory = null }
                            SmallMenuIconButton(icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "На весь экран", isActive = isFullscreen) { onToggleFullscreen() }
                        }
                    }
                }
            }
        }
    }

    when (menuTab) {
        PlaybackMenuTab.SUBTITLES -> {
            var externalUrl by remember { mutableStateOf("") }
            var showExternalField by remember { mutableStateOf(false) }
            PlaybackOptionDialog("Субтитры", onDismiss = { menuTab = null }) {
                DialogRow("Выключены", !subtitlesEnabled) { onDisableSubtitles(); menuTab = null }
                DialogRow("Свой файл по ссылке", showExternalField) { showExternalField = !showExternalField }
                if (showExternalField) {
                    Column(Modifier.fillMaxWidth().padding(start = ZenithDimens.paddingM, bottom = ZenithDimens.paddingS)) {
                        OutlinedTextField(value = externalUrl, onValueChange = { externalUrl = it }, label = { Text("Ссылка на .srt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { if (externalUrl.isNotBlank()) { onLoadExternalSubtitle(externalUrl); menuTab = null } }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить") }
                    }
                }
                subtitleTracks.forEach { t -> DialogRow(t.label, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t); menuTab = null } }
                if (subtitleTracks.isEmpty()) Text("В потоке нет встроенных субтитров", color = Color.Gray, modifier = Modifier.padding(vertical = ZenithDimens.paddingS))
            }
        }
        else -> {}
    }

    // Аудио/качество/скорость — один общий bottom sheet вместо трёх
    // отдельных иконок в капсуле. moreSheetCategory == null — корневой
    // список из трёх пунктов; иначе — значения внутри выбранной
    // категории с кнопкой "назад" в шапке, тот же лист не закрывается.
    if (moreSheetOpen) {
        MoreSettingsSheet(
            category = moreSheetCategory,
            onSelectCategory = { moreSheetCategory = it },
            onBack = { moreSheetCategory = null },
            onDismiss = { moreSheetOpen = false; moreSheetCategory = null },
            variants = variants,
            currentVariant = currentVariant,
            onSelectVariant = { onSelectVariant(it); moreSheetOpen = false; moreSheetCategory = null },
            audioTracks = audioTracks,
            onSelectAudio = { onSelectAudio(it); moreSheetOpen = false; moreSheetCategory = null },
            playbackSpeed = playbackSpeed,
            onSelectSpeed = { onSelectSpeed(it); moreSheetOpen = false; moreSheetCategory = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSettingsSheet(
    category: PlaybackMenuTab?,
    onSelectCategory: (PlaybackMenuTab) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant?,
    onSelectVariant: (StreamVariant) -> Unit,
    audioTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = ZenithDimens.paddingL)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, "Назад", modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(ZenithDimens.paddingXS))
                }
                Text(
                    when (category) {
                        PlaybackMenuTab.AUDIO -> "Аудиодорожка"
                        PlaybackMenuTab.QUALITY -> "Качество"
                        PlaybackMenuTab.SPEED -> "Скорость"
                        else -> "Ещё настройки"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            when (category) {
                null -> {
                    DialogRow("Аудиодорожка") { onSelectCategory(PlaybackMenuTab.AUDIO) }
                    DialogRow("Качество") { onSelectCategory(PlaybackMenuTab.QUALITY) }
                    DialogRow("Скорость") { onSelectCategory(PlaybackMenuTab.SPEED) }
                }
                PlaybackMenuTab.AUDIO -> {
                    if (audioTracks.isEmpty()) Text("Только одна дорожка", color = Color.Gray, modifier = Modifier.padding(horizontal = ZenithDimens.paddingM))
                    audioTracks.forEach { t -> DialogRow(t.label, t.isSelected) { onSelectAudio(t) } }
                }
                PlaybackMenuTab.QUALITY -> {
                    variants.forEach { v -> DialogRow(v.quality + " · " + v.source, v == currentVariant) { onSelectVariant(v) } }
                }
                PlaybackMenuTab.SPEED -> {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        DialogRow(if (s == 1f) "Обычная" else "${s}x", s == playbackSpeed) { onSelectSpeed(s) }
                    }
                }
                PlaybackMenuTab.SUBTITLES -> {} // субтитры в этот лист не входят, своя иконка/диалог
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    // Крупная кнопка play/pause уменьшена с 56dp до 48dp — это ровно
    // минимальный тач-таргет по рекомендациям Android, дальше сжимать
    // уже в ущерб точности попадания пальцем.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(if (isPrimary) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(if (isPrimary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(if (isPrimary) 26.dp else 20.dp))
        }
    }
}

/**
 * Иконки настроек капсулы (субтитры/"⋮"/fullscreen) — раньше видимый
 * цветной чип и тач-зона совпадали (36dp/36dp), из-за чего реальная
 * область попадания была меньше рекомендованного Android-минимума
 * (48dp). Теперь видимый чип уменьшен до 32dp, а тач-зона у самого
 * IconButton — 44dp: капсула визуально компактнее, но пальцем попадать
 * не сложнее, а местами даже проще.
 */
@Composable
private fun SmallMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
        ) {
            Icon(icon, contentDescription, tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Раньше — самописный pointerInput с ДВУМЯ параллельно запущенными
 * жест-детекторами (см. комментарий вверху файла про гонку tap/drag).
 * Теперь — стандартный Material3 Slider: жест обрабатывает библиотечный
 * код (drag-anywhere-on-track из коробки, без отдельного "ползунка" —
 * thumb намеренно пустой, чтобы визуально ничего не изменилось), а
 * растущий трек/буферизация/градиент рисуются в кастомном track-слоте.
 *
 * onPreview вызывается на каждое движение пальца через onValueChange (для
 * обновления текста текущего времени вживую, как во время перетаскивания
 * у YouTube/большинства видеоплееров) — сам onSeekTo вызывается только
 * один раз, через onValueChangeFinished, ровно по отпусканию/завершению
 * жеста (гарантия самой библиотеки, не наша).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrubberBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onPreview: (Long?) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // dragValue != null, пока палец на баре — обновляется на каждое
    // движение через onValueChange. Играет ту же роль, что
    // isScrubbing/scrubTargetMs в прежней реализации, только источник
    // истины теперь один (сам Slider), а не два гоняющихся детектора.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val playedFraction = dragValue ?: (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val isDragging = dragValue != null

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
        label = "phoneScrubberHeight"
    )

    Slider(
        value = playedFraction,
        onValueChange = { fraction ->
            dragValue = fraction
            onPreview((fraction * safeDuration).toLong())
        },
        onValueChangeFinished = {
            val target = ((dragValue ?: playedFraction) * safeDuration).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
            onSeekTo(target)
            dragValue = null
            onPreview(null)
        },
        modifier = modifier,
        thumb = {}, // без видимого "ползунка" — тот же минималистичный вид, что был у самописного бара
        track = { sliderState ->
            ScrubberTrack(
                playedFraction = sliderState.value,
                bufferedFraction = bufferedFraction,
                height = trackHeight,
                isDragging = isDragging
            )
        }
    )
}

/**
 * Три слоя вместо одного (было: только фон + пройденная часть):
 * фон → буферизация (ExoPlayer.bufferedPosition, был доступен и раньше,
 * но нигде не читался) → пройденная часть градиентом вместо сплошной
 * заливки. Плюс мягкое свечение у переднего края во время перетаскивания
 * (тот же приём, что и рост толщины, только не толщина, а пятно света).
 * BoxWithConstraints — чтобы получить реальную ширину трека в Dp и
 * поставить свечение точно на границу пройденной части, а не гадать
 * через выравнивание.
 */
@Composable
private fun ScrubberTrack(playedFraction: Float, bufferedFraction: Float, height: Dp, isDragging: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = maxWidth
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)))
        if (bufferedFraction > playedFraction) {
            Box(Modifier.width(trackWidth * bufferedFraction).height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.4f)))
        }
        Box(
            Modifier.width(trackWidth * playedFraction).height(height).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, ZenithSecondary)))
        )
        if (isDragging) {
            val glowSize = height * 3f
            Box(
                Modifier
                    .offset(x = trackWidth * playedFraction - glowSize / 2)
                    .size(glowSize)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(ZenithSecondary.copy(alpha = 0.5f), Color.Transparent)))
            )
        }
    }
}

@Composable
private fun PlaybackOptionDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun DialogRow(label: String, isSelected: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
