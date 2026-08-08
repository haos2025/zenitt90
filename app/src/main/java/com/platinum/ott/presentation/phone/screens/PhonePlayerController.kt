package com.platinum.ott.presentation.phone.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.presentation.screens.player.PlaybackMenuTab
import com.platinum.ott.presentation.screens.player.TrackOption

// Раньше у плеера на телефоне не было вообще никакого своего UI — только
// дефолтный контроллер ExoPlayer (PlayerView.useController = true): без
// переключения качества/аудио/субтитров вообще (на TV качество уже было,
// аудио и субтитры не было нигде — ни там, ни там).
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
    isFullscreen: Boolean = true,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuTab by remember { mutableStateOf<PlaybackMenuTab?>(null) }

    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.2f).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))))

            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { menuTab = PlaybackMenuTab.SUBTITLES }) { Icon(Icons.Default.ClosedCaption, "Субтитры", tint = if (subtitlesEnabled) Color(0xFF6C63FF) else Color.White) }
                IconButton(onClick = { menuTab = PlaybackMenuTab.SPEED }) { Icon(Icons.Default.Speed, "Скорость", tint = if (playbackSpeed != 1f) Color(0xFF6C63FF) else Color.White) }
                if (variants.size > 1) {
                    IconButton(onClick = { menuTab = PlaybackMenuTab.QUALITY }) { Icon(Icons.Default.HighQuality, "Качество", tint = Color.White) }
                }
                // Раньше плеер на телефоне мог играть только в портретной
                // ориентации целиком приложения — MainActivity форсит
                // SCREEN_ORIENTATION_PORTRAIT глобально для не-TV, и ничего
                // не переопределяло это именно на экране плеера.
                IconButton(onClick = onToggleFullscreen) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "На весь экран", tint = Color.White)
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                var sliderPosition by remember(currentPositionMs) { mutableStateOf(currentPositionMs.toFloat()) }
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF6C63FF), activeTrackColor = Color(0xFF6C63FF))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentPositionMs), color = Color.White)
                    Text(formatMs(durationMs), color = Color.White.copy(0.6f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSeekBackward) { Icon(Icons.Default.Replay10, "-10с", tint = Color.White) }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(56.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Пауза" else "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = onSeekForward) { Icon(Icons.Default.Forward10, "+10с", tint = Color.White) }
                }
            }
        }
    }

    when (menuTab) {
        PlaybackMenuTab.QUALITY -> PlaybackOptionDialog("Качество", onDismiss = { menuTab = null }) {
            variants.forEach { v ->
                DialogRow((if (v == currentVariant) "✓ " else "") + v.quality + " · " + v.source) { onSelectVariant(v); menuTab = null }
            }
        }
        PlaybackMenuTab.AUDIO -> PlaybackOptionDialog("Аудиодорожка", onDismiss = { menuTab = null }) {
            if (audioTracks.isEmpty()) Text("Только одна дорожка", color = Color.Gray, modifier = Modifier.padding(12.dp))
            audioTracks.forEach { t -> DialogRow((if (t.isSelected) "✓ " else "") + t.label) { onSelectAudio(t); menuTab = null } }
        }
        PlaybackMenuTab.SUBTITLES -> {
            var externalUrl by remember { mutableStateOf("") }
            PlaybackOptionDialog("Субтитры", onDismiss = { menuTab = null }) {
                DialogRow((if (!subtitlesEnabled) "✓ " else "") + "Выключены") { onDisableSubtitles(); menuTab = null }
                subtitleTracks.forEach { t -> DialogRow((if (subtitlesEnabled && t.isSelected) "✓ " else "") + t.label) { onSelectSubtitle(t); menuTab = null } }
                if (subtitleTracks.isEmpty()) Text("В потоке нет встроенных субтитров", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                // Внешние SRT по ссылке — раньше субтитры могли быть только
                // встроенные в сам поток.
                OutlinedTextField(value = externalUrl, onValueChange = { externalUrl = it }, label = { Text("Ссылка на .srt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { if (externalUrl.isNotBlank()) { onLoadExternalSubtitle(externalUrl); menuTab = null } }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить по ссылке") }
            }
        }
        PlaybackMenuTab.SPEED -> PlaybackOptionDialog("Скорость", onDismiss = { menuTab = null }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                DialogRow((if (s == playbackSpeed) "✓ " else "") + (if (s == 1f) "Обычная" else "${s}x")) { onSelectSpeed(s); menuTab = null }
            }
        }
        null -> {}
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
private fun DialogRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
