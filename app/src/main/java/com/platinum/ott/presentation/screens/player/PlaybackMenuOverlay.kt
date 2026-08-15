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
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.ui.theme.*

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
    onScanSubtitleQr: () -> Unit,
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
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .padding(vertical = ZenithDimens.paddingL)
        ) {
            Row(Modifier.padding(horizontal = ZenithDimens.paddingSM, vertical = ZenithDimens.paddingXS), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
                MenuTabButton("Качество", tab == PlaybackMenuTab.QUALITY) { onTabChange(PlaybackMenuTab.QUALITY) }
                MenuTabButton("Аудио", tab == PlaybackMenuTab.AUDIO) { onTabChange(PlaybackMenuTab.AUDIO) }
                MenuTabButton("Субтитры", tab == PlaybackMenuTab.SUBTITLES) { onTabChange(PlaybackMenuTab.SUBTITLES) }
                MenuTabButton("Скорость", tab == PlaybackMenuTab.SPEED) { onTabChange(PlaybackMenuTab.SPEED) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingSM))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS), contentPadding = PaddingValues(horizontal = ZenithDimens.paddingSM)) {
                when (tab) {
                    PlaybackMenuTab.QUALITY -> items(variants, key = { it.url }) { v ->
                        MenuRow(v.quality, v.source, v.url == currentVariant.url) { onSelectVariant(v) }
                    }
                    PlaybackMenuTab.AUDIO -> {
                        if (audioTracks.isEmpty()) item { Text("Только одна дорожка", color = Color.White.copy(0.5f), modifier = Modifier.padding(ZenithDimens.paddingM)) }
                        items(audioTracks) { t -> MenuRow(t.label, null, t.isSelected) { onSelectAudio(t) } }
                    }
                    PlaybackMenuTab.SUBTITLES -> {
                        item { MenuRow("Выключены", null, !subtitlesEnabled) { onDisableSubtitles() } }
                        items(subtitleTracks) { t -> MenuRow(t.label, null, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t) } }
                        if (subtitleTracks.isEmpty()) item { Text("В потоке нет встроенных субтитров", color = Color.White.copy(0.5f), modifier = Modifier.padding(ZenithDimens.paddingM)) }
                        // Телефон-компаньон (ROADMAP.md п.6) — внешние SRT по
                        // ссылке через QR, когда набирать URL пультом неудобно.
                        item { MenuRow("По QR с телефона", null, false) { onScanSubtitleQr() } }
                    }
                    PlaybackMenuTab.SPEED -> items(speedOptions) { s ->
                        MenuRow((if (s == 1f) "Обычная" else "${s}x"), null, s == playbackSpeed) { onSelectSpeed(s) }
                    }
                }
            }

            Spacer(Modifier.height(ZenithDimens.paddingS))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = ZenithFocusContainer, focusedContainerColor = ZenithFocusContainerActive)
            ) { Text("Закрыть", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(ZenithDimens.paddingM)) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive
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
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                subLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f)) }
            }
            if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}
