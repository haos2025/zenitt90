package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.domain.model.StreamVariant

// Раньше это был QualityMenuOverlay.kt — только качество. Аудиодорожки и
// субтитры нигде не переключались вообще: ExoPlayer молча выбирал первую
// подходящую дорожку сам, без возможности вмешаться. Переименовано в
// PlaybackMenuOverlay и добавлены вкладки — второй Menu-кнопки на пульте
// TV обычно нет, так что три раздела вместо трёх разных горячих клавиш.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackMenuOverlay(
    tab: PlaybackMenuTab,
    onTabChange: (PlaybackMenuTab) -> Unit,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant,
    onSelectVariant: (StreamVariant) -> Unit,
    audioTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        Column(
            modifier = Modifier.width(280.dp)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .padding(vertical = 24.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuTabButton("Качество", tab == PlaybackMenuTab.QUALITY) { onTabChange(PlaybackMenuTab.QUALITY) }
                MenuTabButton("Аудио", tab == PlaybackMenuTab.AUDIO) { onTabChange(PlaybackMenuTab.AUDIO) }
                MenuTabButton("Субтитры", tab == PlaybackMenuTab.SUBTITLES) { onTabChange(PlaybackMenuTab.SUBTITLES) }
                MenuTabButton("Скорость", tab == PlaybackMenuTab.SPEED) { onTabChange(PlaybackMenuTab.SPEED) }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                when (tab) {
                    PlaybackMenuTab.QUALITY -> items(variants, key = { it.url }) { v ->
                        MenuRow(v.quality, v.source, v.url == currentVariant.url) { onSelectVariant(v) }
                    }
                    PlaybackMenuTab.AUDIO -> {
                        if (audioTracks.isEmpty()) item { Text("Только одна дорожка", color = Color.White.copy(0.5f), modifier = Modifier.padding(16.dp)) }
                        items(audioTracks) { t -> MenuRow(t.label, null, t.isSelected) { onSelectAudio(t) } }
                    }
                    PlaybackMenuTab.SUBTITLES -> {
                        item { MenuRow("Выключены", null, !subtitlesEnabled) { onDisableSubtitles() } }
                        items(subtitleTracks) { t -> MenuRow(t.label, null, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t) } }
                        if (subtitleTracks.isEmpty()) item { Text("В потоке нет встроенных субтитров", color = Color.White.copy(0.5f), modifier = Modifier.padding(16.dp)) }
                    }
                    PlaybackMenuTab.SPEED -> items(speedOptions) { s ->
                        MenuRow((if (s == 1f) "Обычная" else "${s}x"), null, s == playbackSpeed) { onSelectSpeed(s) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = Color.White.copy(alpha = 0.15f))
            ) { Text("Закрыть", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp)) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF6C63FF) else Color.Transparent,
            focusedContainerColor = if (selected) Color(0xFF6C63FF) else Color.White.copy(0.15f)
        )
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuRow(label: String, subLabel: String?, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.3f) else Color.Transparent,
            focusedContainerColor = Color(0xFF6C63FF).copy(alpha = 0.6f)
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(label, color = if (isSelected) Color(0xFF6C63FF) else Color.White)
                subLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f)) }
            }
            if (isSelected) Text("✓", color = Color(0xFF6C63FF))
        }
    }
}
