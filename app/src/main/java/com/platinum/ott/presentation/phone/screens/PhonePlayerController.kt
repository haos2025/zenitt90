package com.platinum.ott.presentation.phone.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.platinum.ott.domain.model.StreamVariant

// Раньше у плеера на телефоне не было вообще никакого своего UI — только
// дефолтный контроллер ExoPlayer (PlayerView.useController = true): без
// переключения качества (на TV оно уже было — QualityMenuOverlay.kt) и
// без названия фильма во время просмотра.
@Composable
fun PhonePlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    title: String,
    currentPositionMs: Long,
    durationMs: Long,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant?,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectVariant: (StreamVariant) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQualityDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.2f).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))))

            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (variants.size > 1) {
                    IconButton(onClick = { showQualityDialog = true }) { Icon(Icons.Default.HighQuality, "Качество", tint = Color.White) }
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

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Качество") },
            text = {
                Column {
                    variants.forEach { v ->
                        TextButton(onClick = { onSelectVariant(v); showQualityDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (if (v == currentVariant) "✓ " else "") + v.quality + " · " + v.source,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Закрыть") } }
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
