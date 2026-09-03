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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
// вместо текстового "✓".
//
// Аудит пульта (по запросу после правок в PlayerScreen.kt): панель
// открывается поверх уже отрисованного PlayerController — тот никуда не
// девается из композиции (он просто визуально перекрыт), поэтому без
// явного запроса фокуса Compose продолжал бы считать "в фокусе" ту
// иконку капсулы, с которой панель открыли. С пульта в таком состоянии
// докрутить до вкладок/строк самой панели можно было не всегда — зависело
// от взаимного расположения элементов на экране, не гарантированно.
// menuFocusRequester ниже переносит фокус на активную вкладку сразу при
// появлении панели, независимо от того, что было в фокусе до открытия
// (клик по иконке капсулы или клавиша Menu — не важно). Тот же приём в
// QrScanScreen.kt (общая причина).
//
// Второй раунд редизайна (PROMPT_PLAYER_OVERLAY_REDESIGN.md): вкладка
// "Субт." раньше показывала только "Выключены" + встроенные дорожки —
// внешние субтитры по ссылке были доступны на TV ТОЛЬКО через отдельную
// кнопку/QR на самом плеере (см. PlayerController.kt/PlayerScreen.kt,
// showCompanionQr), никак не отражены в этом списке — с телефона это
// выглядело как "список дорожек плюс невзрачное поле ввода снизу", на TV
// вообще не было заметно, что такая возможность есть. Теперь "Свой файл
// по ссылке" — равноправная строка в том же списке; печатать URL пультом
// по-прежнему неудобно, так что клик по ней не открывает поле ввода на
// месте (как на телефоне), а вызывает тот же QR-поток — onRequestExternalSubtitleQr.
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
    onRequestExternalSubtitleQr: () -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    // Переносится на MenuTabButton текущей активной вкладки — см. Row с
    // четырьмя MenuTabButton ниже и комментарий в шапке файла.
    val activeTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { activeTabFocusRequester.requestFocus() }
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
                MenuTabButton("Качество", tab == PlaybackMenuTab.QUALITY, Modifier.weight(1f).let { if (tab == PlaybackMenuTab.QUALITY) it.focusRequester(activeTabFocusRequester) else it }) { onTabChange(PlaybackMenuTab.QUALITY) }
                MenuTabButton("Аудио", tab == PlaybackMenuTab.AUDIO, Modifier.weight(1f).let { if (tab == PlaybackMenuTab.AUDIO) it.focusRequester(activeTabFocusRequester) else it }) { onTabChange(PlaybackMenuTab.AUDIO) }
                MenuTabButton("Субт.", tab == PlaybackMenuTab.SUBTITLES, Modifier.weight(1f).let { if (tab == PlaybackMenuTab.SUBTITLES) it.focusRequester(activeTabFocusRequester) else it }) { onTabChange(PlaybackMenuTab.SUBTITLES) }
                MenuTabButton("Скор.", tab == PlaybackMenuTab.SPEED, Modifier.weight(1f).let { if (tab == PlaybackMenuTab.SPEED) it.focusRequester(activeTabFocusRequester) else it }) { onTabChange(PlaybackMenuTab.SPEED) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(horizontal = ZenithDimens.paddingSM)) {
                when (tab) {
                    PlaybackMenuTab.QUALITY -> items(variants, key = { it.url }) { v ->
                        MenuRow(v.quality, v.source, v.url == currentVariant.url) { onSelectVariant(v) }
                    }
                    PlaybackMenuTab.AUDIO -> {
                        if (audioTracks.isEmpty()) item { EmptyHint("Только одна дорожка") }
                        items(audioTracks) { t -> MenuRow(t.label, null, t.isSelected) { onSelectAudio(t) } }
                    }
                    PlaybackMenuTab.SUBTITLES -> {
                        item { MenuRow("Выключены", null, !subtitlesEnabled) { onDisableSubtitles() } }
                        // Равноправный пункт списка, не поле ввода снизу —
                        // единственный способ получить субтитры-ПЕРЕВОД на
                        // другой язык (встроенные дорожки — это язык
                        // оригинала). trailingIcon вместо чек-иконки: это
                        // действие (открыть QR), а не выбор текущего
                        // состояния, отмечать как "выбрано" нечем.
                        item {
                            MenuRow(
                                label = "Свой файл по ссылке",
                                subLabel = "по QR с телефона",
                                isSelected = false,
                                trailingIcon = Icons.Default.QrCode
                            ) { onRequestExternalSubtitleQr() }
                        }
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
private fun MenuRow(
    label: String,
    subLabel: String?,
    isSelected: Boolean,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
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
            when {
                // Действие (например "Свой файл по ссылке" → QR), а не
                // текущий выбор — trailingIcon показывает ЧТО произойдёт,
                // не помечает строку как "выбранную".
                trailingIcon != null -> Icon(trailingIcon, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                isSelected -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
