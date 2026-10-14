package com.platinum.ott.presentation.screens.player

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.*

/** Действия оверлея плеера на ТВ, в порядке слева направо. */
enum class OverlayAction { PREV, PLAY, NEXT, EPISODES, CHANNELS, PROGRAM, LIVE, SUBTITLES, AUDIO, QUALITY, SPEED }

/** Какие кнопки есть у данного типа воспроизведения (список один и тот же для рисования и для обработки пульта). */
fun overlayActions(kind: PlayerKind, hasPrev: Boolean, hasNext: Boolean): List<OverlayAction> = buildList {
    if (kind == PlayerKind.SERIES && hasPrev) add(OverlayAction.PREV)
    add(OverlayAction.PLAY)
    if (kind == PlayerKind.SERIES && hasNext) add(OverlayAction.NEXT)
    when (kind) {
        PlayerKind.SERIES -> add(OverlayAction.EPISODES)
        PlayerKind.LIVE -> { add(OverlayAction.CHANNELS); add(OverlayAction.PROGRAM) }
        PlayerKind.CATCHUP -> { add(OverlayAction.LIVE); add(OverlayAction.CHANNELS); add(OverlayAction.PROGRAM) }
        PlayerKind.MOVIE -> {}
    }
    add(OverlayAction.SUBTITLES); add(OverlayAction.AUDIO); add(OverlayAction.QUALITY)
    if (kind != PlayerKind.LIVE) add(OverlayAction.SPEED)
}

/**
 * Оверлей плеера для Android TV (ПЕРЕПИСАН).
 *
 * Кнопки здесь НЕ фокусируемые: весь пульт обрабатывает PlayerScreen («виртуальный фокус» —
 * единственный настоящий фокус всегда на корне экрана, а подсвеченная кнопка — это
 * [selected]). Раньше кнопки были настоящими focusable-элементами, и фокус терялся при
 * скрытии оверлея, из-за чего Вниз/Назад переставали работать.
 *
 *  selected == null  — управление «на видео»: Влево/Вправо перематывают, полоса прогресса
 *                      выделена;
 *  selected != null  — подсвечена кнопка ряда.
 *
 * Содержимое зависит от kind: MOVIE — play + настройки; SERIES — ⏮ ⏯ ⏭ «Серии» + настройки;
 * LIVE — «сейчас/далее», «Каналы», «Программа» + настройки; CATCHUP — запись + «В эфир».
 * Подсказок по клавишам нет намеренно.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerController(
    isVisible: Boolean,
    selected: OverlayAction?,
    kind: PlayerKind,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    title: String = "",
    subtitle: String? = null,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    liveInfo: ZapBannerInfo? = null,
    // Растёт ли сейчас прогресс-бар — управляется снаружи (PlayerScreen).
    isSeekActive: Boolean = false,
    // Прогруженная вперёд позиция (0 — не показывать: выключено в настройках или live).
    bufferedPositionMs: Long = 0L,
    subtitlesEnabled: Boolean = false,
    playbackSpeed: Float = 1f,
    modifier: Modifier = Modifier
) {
    val isLive = kind == PlayerKind.LIVE
    val actions = overlayActions(kind, hasPreviousEpisode, hasNextEpisode)
    val videoSelected = selected == null

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        exit = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Верх: метка + название.
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.22f).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            )
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .padding(horizontal = ZenithDimens.tvOverscanPadding, vertical = ZenithDimens.paddingL),
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (kind) {
                    PlayerKind.LIVE -> KindBadge("● ЭФИР", ZenithError)
                    PlayerKind.CATCHUP -> KindBadge("ЗАПИСЬ", MaterialTheme.colorScheme.secondary)
                    else -> {}
                }
                Column {
                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface.copy(alpha = 0.65f), maxLines = 1)
                }
            }

            // Низ: прогресс (или «сейчас в эфире») и ряд кнопок на тёмном градиенте, без рамки-капсулы.
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .padding(horizontal = ZenithDimens.tvOverscanPadding)
                    .padding(top = ZenithDimens.paddingXXL, bottom = ZenithDimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
            ) {
                if (isLive) {
                    LiveNowNext(liveInfo)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(currentPositionMs), style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface.copy(alpha = 0.85f))
                        Spacer(Modifier.width(ZenithDimens.paddingM))
                        ProgressBar(currentPositionMs, bufferedPositionMs, durationMs, isActive = isSeekActive, emphasized = videoSelected, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(ZenithDimens.paddingM))
                        Text(formatTime(durationMs), style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface.copy(alpha = 0.5f))
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        actions.filter { it.isLeftGroup() }.forEach { a ->
                            when (a) {
                                OverlayAction.PREV -> RoundButton(Icons.Default.SkipPrevious, "Предыдущая серия", 48.dp, selected == a)
                                OverlayAction.PLAY -> RoundButton(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (isPlaying) "Пауза" else "Смотреть", 60.dp, selected == a, isPrimary = true
                                )
                                OverlayAction.NEXT -> RoundButton(Icons.Default.SkipNext, "Следующая серия", 48.dp, selected == a)
                                OverlayAction.EPISODES -> Pill(Icons.Default.VideoLibrary, "Серии", selected == a)
                                OverlayAction.CHANNELS -> Pill(Icons.Default.Tv, "Каналы", selected == a)
                                OverlayAction.PROGRAM -> Pill(Icons.Default.CalendarMonth, "Программа", selected == a)
                                OverlayAction.LIVE -> Pill(Icons.Default.FiberManualRecord, "В эфир", selected == a)
                                else -> {}
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                        actions.filter { !it.isLeftGroup() }.forEach { a ->
                            when (a) {
                                OverlayAction.SUBTITLES -> SquareButton(Icons.Default.ClosedCaption, "Субтитры", selected == a, active = subtitlesEnabled)
                                OverlayAction.AUDIO -> SquareButton(Icons.Default.Audiotrack, "Аудиодорожка", selected == a, active = false)
                                OverlayAction.QUALITY -> SquareButton(Icons.Default.HighQuality, "Качество", selected == a, active = false)
                                OverlayAction.SPEED -> SquareButton(Icons.Default.Speed, "Скорость", selected == a, active = playbackSpeed != 1f)
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun OverlayAction.isLeftGroup(): Boolean = when (this) {
    OverlayAction.SUBTITLES, OverlayAction.AUDIO, OverlayAction.QUALITY, OverlayAction.SPEED -> false
    else -> true
}

@Composable
private fun KindBadge(text: String, color: Color) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = Color.White,
        modifier = Modifier.clip(ZenithShapePill).background(color).padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Ряд 1 для живого канала: что идёт сейчас (с прогрессом передачи) и что следом. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveNowNext(info: ZapBannerInfo?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (info?.currentProgramTitle == null) {
            Text("Прямой эфир", style = MaterialTheme.typography.bodyLarge, color = ZenithOnSurface.copy(alpha = 0.85f))
            if (info != null) Text("Нет данных о программе передач", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface.copy(alpha = 0.5f))
        } else {
            Text(
                "${info.channelNumber}  ·  ${info.currentProgramTitle}",
                style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (info.currentProgramProgress != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(ZenithShapePill).background(ZenithOnSurface.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(info.currentProgramProgress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
            if (info.nextProgramTitle != null) {
                Text("Далее: ${info.nextProgramTitle}", style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Подсвеченная кнопка — залита белым с тёмной иконкой (хорошо видна с дивана);
// неподсвеченные — полупрозрачные.
private val SelectedFill = Color.White
private val SelectedContent = Color(0xFF101114)

@Composable
private fun RoundButton(icon: ImageVector, contentDescription: String, size: Dp, selected: Boolean, isPrimary: Boolean = false) {
    val container = when {
        selected -> SelectedFill
        isPrimary -> MaterialTheme.colorScheme.primary
        else -> ZenithOnSurface.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (selected) SelectedContent else if (isPrimary) Color(0xFF06222B) else ZenithOnSurface, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, selected: Boolean) {
    Row(
        Modifier.clip(ZenithShapePill).background(if (selected) SelectedFill else ZenithOnSurface.copy(alpha = 0.14f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) SelectedContent else ZenithOnSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) SelectedContent else ZenithOnSurface, maxLines = 1)
    }
}

@Composable
private fun SquareButton(icon: ImageVector, contentDescription: String, selected: Boolean, active: Boolean) {
    val shape: Shape = CircleShape
    Box(
        Modifier.size(46.dp).clip(shape)
            .background(
                when {
                    selected -> SelectedFill
                    active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    else -> ZenithOnSurface.copy(alpha = 0.14f)
                }
            )
            .then(if (active && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription,
            tint = if (selected) SelectedContent else if (active) MaterialTheme.colorScheme.primary else ZenithOnSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ProgressBar(currentMs: Long, bufferedMs: Long, durationMs: Long, isActive: Boolean, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 5dp в покое → 8dp, когда выделена полоса (управление «на видео») → 12dp при перемотке.
    val height by animateDpAsState(
        targetValue = if (isActive) 12.dp else if (emphasized) 8.dp else 5.dp,
        animationSpec = tween(durationMillis = ZenithDurationShort, easing = ZenithEasingStandard),
        label = "tvProgressBarHeight"
    )
    Box(modifier = modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(ZenithShapePill).background(ZenithOnSurface.copy(alpha = 0.2f))) {
            val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            if (buffered > progress) Box(Modifier.fillMaxWidth(buffered).fillMaxHeight().background(ZenithOnSurface.copy(alpha = 0.32f)))
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}

/** Форматирует миллисекунды в MM:SS или HH:MM:SS */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
