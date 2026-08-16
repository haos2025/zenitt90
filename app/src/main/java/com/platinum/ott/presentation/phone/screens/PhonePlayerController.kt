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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
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
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingM),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Транспортная капсула — та же визуальная идея, что и на TV:
                // не голый ряд иконок поверх видео, а собственная плашка.
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TransportButton(
                            icon = if (isSeries) Icons.Default.SkipPrevious else androidx.compose.material.icons.Icons.Default.Replay10,
                            contentDescription = if (isSeries) "Предыдущая серия" else "-10 секунд",
                            enabled = !isSeries || hasPreviousEpisode,
                            onClick = if (isSeries) onPreviousEpisode else onSeekBackward
                        )
                        Spacer(Modifier.width(ZenithDimens.paddingL))
                        TransportButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Play",
                            isPrimary = true,
                            onClick = onTogglePlay
                        )
                        Spacer(Modifier.width(ZenithDimens.paddingL))
                        TransportButton(
                            icon = if (isSeries) Icons.Default.SkipNext else androidx.compose.material.icons.Icons.Default.Forward10,
                            contentDescription = if (isSeries) "Следующая серия" else "+10 секунд",
                            enabled = !isSeries || hasNextEpisode,
                            onClick = if (isSeries) onNextEpisode else onSeekForward
                        )
                    }
                }

                Spacer(Modifier.height(ZenithDimens.paddingS))

                // Раньше субтитры/скорость/качество/fullscreen были в правом
                // верхнем углу — в альбомной ориентации, когда телефон держат
                // двумя руками, большие пальцы естественно лежат внизу
                // экрана. Теперь под транспортной капсулой, тоже внизу.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { menuTab = PlaybackMenuTab.SUBTITLES }) { Icon(Icons.Default.ClosedCaption, "Субтитры", tint = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White) }
                    IconButton(onClick = { menuTab = PlaybackMenuTab.SPEED }) { Icon(Icons.Default.Speed, "Скорость", tint = if (playbackSpeed != 1f) MaterialTheme.colorScheme.primary else Color.White) }
                    if (variants.size > 1) {
                        IconButton(onClick = { menuTab = PlaybackMenuTab.QUALITY }) { Icon(Icons.Default.HighQuality, "Качество", tint = Color.White) }
                    }
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "На весь экран", tint = Color.White)
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
            PlaybackOptionDialog("Субтитры", onDismiss = { menuTab = null }) {
                DialogRow("Выключены", !subtitlesEnabled) { onDisableSubtitles(); menuTab = null }
                subtitleTracks.forEach { t -> DialogRow(t.label, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t); menuTab = null } }
                if (subtitleTracks.isEmpty()) Text("В потоке нет встроенных субтитров", color = Color.Gray, modifier = Modifier.padding(vertical = ZenithDimens.paddingS))
                Spacer(Modifier.height(ZenithDimens.paddingS))
                // Внешние SRT по ссылке остаются здесь — телефон, в отличие
                // от TV, печатает без проблем, отдельный QR-канал самому
                // себе не нужен (QR — только чтобы TV принимал текст от
                // телефона, см. ROADMAP.md п.6). Общее включено/выключено
                // по умолчанию — теперь в Настройки → Воспроизведение →
                // Субтитры (см. PhoneSettingsScreen.kt), не здесь: это
                // относится ко всем видео сразу, а не к конкретному фильму.
                OutlinedTextField(value = externalUrl, onValueChange = { externalUrl = it }, label = { Text("Ссылка на .srt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { if (externalUrl.isNotBlank()) { onLoadExternalSubtitle(externalUrl); menuTab = null } }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить по ссылке") }
            }
        }
        PlaybackMenuTab.SPEED -> PlaybackOptionDialog("Скорость", onDismiss = { menuTab = null }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                DialogRow(if (s == 1f) "Обычная" else "${s}x", s == playbackSpeed) { onSelectSpeed(s); menuTab = null }
            }
        }
        null -> {}
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
