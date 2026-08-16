package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.ui.theme.*

// Редизайн (было: QualityMenuOverlay.kt → PlaybackMenuOverlay — плоская
// боковая панель без скруглений/рамки, вкладки — просто текст с заливкой,
// строки — сплошные плашки на всю ширину). Теперь: та же четырёхвкладочная
// структура (Качество/Аудио/Субтитры/Скорость — на пульте без второй
// Menu-кнопки удобнее одна панель с разделами, чем разные горячие
// клавиши), но с закруглённой карточкой, отступом от края экрана (не
// впритык), сегментированными вкладками-пилюлями и строками с чек-иконкой
// вместо текстового "✓". "По QR с телефона" убрано из списка субтитровых
// дорожек — семантически другое действие (подключение устройства, не
// выбор дорожки), теперь отдельная кнопка на самом плеере (см.
// PlayerController.kt), не спрятана в этом списке.
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(
            modifier = Modifier
                .padding(ZenithDimens.paddingL)
                .width(320.dp)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(ZenithSurface.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(vertical = ZenithDimens.paddingL)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Настройки", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = ZenithFocusContainerActive)
                ) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp).size(18.dp)) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            // Сегментированные вкладки-пилюли в общей "капсуле" вместо
            // отдельных прямоугольников — компактнее, читается как одна
            // группа переключателей, а не четыре разные кнопки.
            Row(
                Modifier.padding(horizontal = ZenithDimens.paddingM)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MenuTabButton("Качество", tab == PlaybackMenuTab.QUALITY, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.QUALITY) }
                MenuTabButton("Аудио", tab == PlaybackMenuTab.AUDIO, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.AUDIO) }
                MenuTabButton("Субт.", tab == PlaybackMenuTab.SUBTITLES, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.SUBTITLES) }
                MenuTabButton("Скор.", tab == PlaybackMenuTab.SPEED, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.SPEED) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(horizontal = ZenithDimens.paddingSM)) {
                when (tab) {
                    PlaybackMenuTab.QUALITY -> items(variants, key = { it.url }) { v ->
                        MenuRow(v.quality, v.source, v.url == currentVariant.url) { onSelectVariant(v) }
                    }
                    PlaybackMenuTab.AUDIO -> {
                        if (audioTracks.isEmpty()) EmptyHint("Только одна дорожка")
                        items(audioTracks) { t -> MenuRow(t.label, null, t.isSelected) { onSelectAudio(t) } }
                    }
                    PlaybackMenuTab.SUBTITLES -> {
                        item { MenuRow("Выключены", null, !subtitlesEnabled) { onDisableSubtitles() } }
                        items(subtitleTracks) { t -> MenuRow(t.label, null, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t) } }
                        if (subtitleTracks.isEmpty()) item { EmptyHint("В потоке нет встроенных субтитров") }
                    }
                    PlaybackMenuTab.SPEED -> items(speedOptions) { s ->
                        MenuRow((if (s == 1f) "Обычная" else "${s}x"), null, s == playbackSpeed) { onSelectSpeed(s) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun EmptyHint(text: String) {
    Text(text, color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(ZenithDimens.paddingM))
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuTabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive
        )
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuRow(label: String, subLabel: String?, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = ZenithFocusContainerActive
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                subLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.45f)) }
            }
            if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
