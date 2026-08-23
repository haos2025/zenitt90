package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.*

/**
 * Контроллер плеера для Android TV — редизайн (было: полноширинный
 * градиент от края до края экрана + три плоские кнопки-прямоугольника с
 * эмодзи-глифами вместо иконок). Теперь: плавающая "капсула" по центру
 * снизу — узнаваемый язык медиаплееров (YouTube TV, Apple TV), не размазан
 * во весь экран. Раньше две крайние кнопки были ТОЛЬКО перемоткой на
 * ±10с — при просмотре сериала (nextEpisodeId/previousEpisodeId заданы,
 * см. PlayerViewModel.loadMovie) это теперь следующий/предыдущий эпизод;
 * для обычного фильма — перемотка, как и было, сама возможность перемотки
 * никуда не делась (те же onSeekForward/onSeekBackward, просто кнопки
 * заняты под эпизоды когда есть сериал — перемотка на TV всё ещё доступна
 * с D-pad Left/Right, см. PlayerScreen.onKeyEvent).
 *
 * Редизайн оверлея (второй раунд, PROMPT_PLAYER_OVERLAY_REDESIGN.md):
 * качество/аудио/субтитры/скорость раньше жили ТОЛЬКО в отдельной панели
 * (PlaybackMenuOverlay), доступной по Menu/DirectionUp — на самой капсуле
 * не было ни одной иконки, указывающей на это. Теперь у капсулы справа
 * есть те же четыре иконки, каждая открывает PlaybackMenuOverlay сразу на
 * своей вкладке одним действием (см. onOpenMenuTab/PlayerViewModel.
 * openPlaybackMenu). В отличие от кнопок перемотки/эпизодов слева, эти
 * четыре — НАСТОЯЩИЕ интерактивные TV-компоненты (tv-material3 Surface,
 * получают фокус пульта нормально), потому что взаимодействие с ними
 * реально ожидается через клик/OK на сфокусированной иконке, а не только
 * через глобальные горячие клавиши.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlay: () -> Unit,
    title: String = "",
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    onConnectPhone: () -> Unit = {},
    // Состояние для подсветки активных иконок справа — то же самое, что
    // уже частично было на телефоне (subtitlesEnabled), плюс скорость.
    // Для аудио/качества устойчивого понятия "активно" нет (нет
    // единого дефолта, с которым сравнивать), поэтому эти две иконки
    // всегда нейтральные — кликабельны, просто без подсветки состояния.
    subtitlesEnabled: Boolean = false,
    playbackSpeed: Float = 1f,
    onOpenMenuTab: (PlaybackMenuTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSeries = hasNextEpisode || hasPreviousEpisode

    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn(),
        exit    = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            if (title.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.22f).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .padding(horizontal = ZenithDimens.paddingXXL, vertical = ZenithDimens.paddingXL),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    // Телефон-компаньон вынесен из вложенного меню
                    // "Субтитры" (было — терялся среди дорожек, семантически
                    // другое действие) на верхний уровень — доступен одним
                    // нажатием, не через два вложенных экрана.
                    IconGlyphButton(icon = Icons.Default.PhoneAndroid, contentDescription = "Подключить телефон", onClick = onConnectPhone, size = 44.dp)
                }
            }

            // Капсула — не на весь экран, у неё собственная плашка с рамкой
            // и скруглением, а не растянутый на всю ширину градиент.
            // Ширина увеличена относительно первой версии редизайна — та
            // же плашка теперь вмещает ещё четыре иконки справа от
            // транспорта, без этого им было бы тесно.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ZenithDimens.paddingXXL)
                    .widthIn(max = 860.dp)
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ZenithSurface.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = ZenithDimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                // Тонкий прогресс-бар с временем в ОДНУ строку (текущее
                // слева от бара, длительность справа) — раньше уже было
                // так, макет редизайна намеренно это сохраняет, не
                // растягивать обратно в отдельную строку над/под баром.
                ProgressBar(currentMs = currentPositionMs, durationMs = durationMs)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                }

                // Один ряд: слева — транспорт (перемотка/эпизоды + play-
                // pause чуть крупнее в этом же кластере), справа — четыре
                // маленькие квадратные иконки настроек воспроизведения.
                // Без подписей под иконками — состояние видно по
                // подсветке, подписи только раздували бы высоту капсулы.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconGlyphButton(
                            icon = if (isSeries) Icons.Default.SkipPrevious else Icons.Default.Replay10,
                            contentDescription = if (isSeries) "Предыдущая серия" else "-10 секунд",
                            onClick = if (isSeries) onPreviousEpisode else onSeekBackward,
                            enabled = !isSeries || hasPreviousEpisode,
                            size = 52.dp
                        )
                        Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                        IconGlyphButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                            onClick = onTogglePlay,
                            isPrimary = true,
                            size = 68.dp
                        )
                        Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                        IconGlyphButton(
                            icon = if (isSeries) Icons.Default.SkipNext else Icons.Default.Forward10,
                            contentDescription = if (isSeries) "Следующая серия" else "+10 секунд",
                            onClick = if (isSeries) onNextEpisode else onSeekForward,
                            enabled = !isSeries || hasNextEpisode,
                            size = 52.dp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                        MenuIconButton(icon = Icons.Default.ClosedCaption, contentDescription = "Субтитры", isActive = subtitlesEnabled) { onOpenMenuTab(PlaybackMenuTab.SUBTITLES) }
                        MenuIconButton(icon = Icons.Default.Audiotrack, contentDescription = "Аудиодорожка", isActive = false) { onOpenMenuTab(PlaybackMenuTab.AUDIO) }
                        MenuIconButton(icon = Icons.Default.HighQuality, contentDescription = "Качество", isActive = false) { onOpenMenuTab(PlaybackMenuTab.QUALITY) }
                        MenuIconButton(icon = Icons.Default.Speed, contentDescription = "Скорость", isActive = playbackSpeed != 1f) { onOpenMenuTab(PlaybackMenuTab.SPEED) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    // Box+clickable, не Surface — D-pad уже обрабатывается глобально в
    // PlayerScreen.onKeyEvent, эти кнопки не получают фокус напрямую и не
    // нуждаются в отдельной focus/indication-логике (то же решение, что
    // было в старой ControlButton). Это осознанно НЕ меняли в этом
    // редизайне — попытка притвориться, что они фокусируемые, была бы
    // хуже честного decorative-состояния (см. центральную надпись
    // "10 сек"/название серии в PlayerScreen.kt, которая теперь реально
    // даёт обратную связь на DirectionLeft/DirectionRight).
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
            .clip(CircleShape)
            .background(
                when {
                    isPrimary -> MaterialTheme.colorScheme.primary
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
    ) {
        Icon(
            icon, contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Четыре маленькие квадратные иконки справа (субтитры/аудио/качество/
 * скорость) — в отличие от IconGlyphButton выше, это НАСТОЯЩИЙ
 * фокусируемый TV-компонент (tv-material3 Surface), потому что клик по
 * каждой должен реально работать с пульта, не быть декоративным.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else ZenithFocusContainerActive
        )
    ) {
        Icon(
            icon, contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.padding(9.dp).size(20.dp)
        )
    }
}

@Composable
private fun ProgressBar(currentMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

/** Форматирует миллисекунды в MM:SS или HH:MM:SS */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
