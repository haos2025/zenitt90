package com.platinum.ott.presentation.phone.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
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
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.presentation.screens.player.PlaybackMenuTab
import com.platinum.ott.presentation.screens.player.TrackOption
import com.platinum.ott.ui.theme.ZenithSurface

// Редизайн (было: прямые IconButton без общей плашки, семь иконок в один
// ряд снизу без иерархии — перемотка/пауза, качество/скорость/субтитры/
// fullscreen все одного размера и веса). Транспортный ряд (перемотка или
// эпизоды + play/pause) — в собственной капсуле, как на TV
// (PlayerController.kt) — тот же язык на обеих платформах. Раньше кнопки
// -10с/+10с были единственным способом перемотки НА ЭКРАНЕ — жест свайпа
// (см. PhonePlayerScreen.kt, seekDeltaFromDrag) уже делает то же самое,
// так что при просмотре сериала (hasNextEpisode/hasPreviousEpisode) эти
// две кнопки не теряют перемотку вообще, она остаётся жестом.
//
// Второй раунд редизайна (PROMPT_PLAYER_OVERLAY_REDESIGN.md): раньше
// субтитры/скорость/качество/fullscreen были ОТДЕЛЬНЫМ рядом голых иконок
// без собственного фона ПОД транспортной капсулой — выглядело как два
// разных элемента UI, не один плеер. Теперь один Column с общим фоном/
// рамкой/скруглением — транспорт и четыре иконки настроек в одной и той
// же плашке, в одном ряду (транспорт слева, иконки справа). Fullscreen
// вынесен из этого ряда наверх, к заголовку — это переключатель ориентации
// экрана, а не параметр воспроизведения текущего видео, как остальные
// четыре (которые теперь буквально совпадают с четырьмя вкладками
// PlaybackMenuOverlay на TV: субтитры/аудио/качество/скорость — раньше
// кнопки аудиодорожки на телефоне не было вообще).
@Composable
fun PhonePlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    title: String,
    currentPositionMs: Long,
    durationMs: Long,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant?,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
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
    val isSeries = hasNextEpisode || hasPreviousEpisode

    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.3f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))

            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // Fullscreen — переключатель ориентации экрана целиком, не
                // параметр воспроизведения текущего видео (как остальные
                // четыре иконки капсулы ниже) — поэтому живёт в верхней
                // строке рядом с заголовком, а не внутри транспортной
                // плашки.
                IconButton(onClick = onToggleFullscreen) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "На весь экран", tint = Color.White)
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingM),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Единая капсула — транспорт и ряд иконок настроек теперь
                // один Column с общим фоном/рамкой/скруглением (раньше
                // иконки были отдельным элементом без своего фона под
                // капсулой).
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(ZenithSurface.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM)
                ) {
                    var sliderPosition by remember(currentPositionMs) { mutableStateOf(currentPositionMs.toFloat()) }
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = ZenithDimens.paddingXS), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(currentPositionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(formatMs(durationMs), color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                    }

                    // Один ряд: слева — транспортный кластер (перемотка/
                    // эпизоды + play/pause покрупнее посередине кластера),
                    // справа — четыре маленькие квадратные иконки настроек
                    // воспроизведения. Тот же макет, что на TV
                    // (PlayerController.kt).
                    Row(
                        Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TransportButton(
                                icon = if (isSeries) Icons.Default.SkipPrevious else Icons.Default.Replay10,
                                contentDescription = if (isSeries) "Предыдущая серия" else "-10 секунд",
                                enabled = !isSeries || hasPreviousEpisode,
                                onClick = if (isSeries) onPreviousEpisode else onSeekBackward
                            )
                            Spacer(Modifier.width(ZenithDimens.paddingM))
                            TransportButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Play",
                                isPrimary = true,
                                onClick = onTogglePlay
                            )
                            Spacer(Modifier.width(ZenithDimens.paddingM))
                            TransportButton(
                                icon = if (isSeries) Icons.Default.SkipNext else Icons.Default.Forward10,
                                contentDescription = if (isSeries) "Следующая серия" else "+10 секунд",
                                enabled = !isSeries || hasNextEpisode,
                                onClick = if (isSeries) onNextEpisode else onSeekForward
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                            SmallMenuIconButton(icon = Icons.Default.ClosedCaption, contentDescription = "Субтитры", isActive = subtitlesEnabled) { menuTab = PlaybackMenuTab.SUBTITLES }
                            SmallMenuIconButton(icon = Icons.Default.Audiotrack, contentDescription = "Аудиодорожка", isActive = false) { menuTab = PlaybackMenuTab.AUDIO }
                            SmallMenuIconButton(icon = Icons.Default.HighQuality, contentDescription = "Качество", isActive = false) { menuTab = PlaybackMenuTab.QUALITY }
                            SmallMenuIconButton(icon = Icons.Default.Speed, contentDescription = "Скорость", isActive = playbackSpeed != 1f) { menuTab = PlaybackMenuTab.SPEED }
                        }
                    }
                }
            }
        }
    }

    when (menuTab) {
        PlaybackMenuTab.QUALITY -> PlaybackOptionDialog("Качество", onDismiss = { menuTab = null }) {
            variants.forEach { v -> DialogRow(v.quality + " · " + v.source, v == currentVariant) { onSelectVariant(v); menuTab = null } }
        }
        PlaybackMenuTab.AUDIO -> PlaybackOptionDialog("Аудиодорожка", onDismiss = { menuTab = null }) {
            if (audioTracks.isEmpty()) Text("Только одна дорожка", color = Color.Gray, modifier = Modifier.padding(ZenithDimens.paddingSM))
            audioTracks.forEach { t -> DialogRow(t.label, t.isSelected) { onSelectAudio(t); menuTab = null } }
        }
        PlaybackMenuTab.SUBTITLES -> {
            var externalUrl by remember { mutableStateOf("") }
            // "Свой файл по ссылке" раньше был полем ввода мелким шрифтом
            // внизу диалога, отдельно от остальных пунктов. Теперь —
            // равноправная строка списка (как "Выключены"/дорожки); клик
            // по ней разворачивает то же поле ввода прямо под ней, а не
            // держит его видимым постоянно.
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
        PlaybackMenuTab.SPEED -> PlaybackOptionDialog("Скорость", onDismiss = { menuTab = null }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                DialogRow((if (s == 1f) "Обычная" else "${s}x"), s == playbackSpeed) { onSelectSpeed(s); menuTab = null }
            }
        }
        null -> {}
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(if (isPrimary) 56.dp else 44.dp)
            .clip(CircleShape)
            .background(if (isPrimary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(if (isPrimary) 30.dp else 22.dp))
        }
    }
}

/**
 * Четыре маленькие квадратные иконки настроек (субтитры/аудио/качество/
 * скорость) — тот же визуальный язык, что и на TV (MenuIconButton в
 * PlayerController.kt): закруглённый квадрат, активная — с подсветкой
 * фоном/цветом иконки, неактивная — нейтральная полупрозрачная подложка.
 */
@Composable
private fun SmallMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription, tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(18.dp))
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
