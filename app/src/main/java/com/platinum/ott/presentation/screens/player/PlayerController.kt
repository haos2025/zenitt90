package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
 * Контроллер плеера для Android TV.
 *
 * Третий раунд (убраны декоративные ±10с): кнопки ±10с в транспортном
 * кластере НИКОГДА не были реально интерактивными на TV — они не получают
 * фокус пульта (см. IconGlyphButton ниже, clickable без индикации focus),
 * настоящая перемотка всегда шла мимо них, через глобальный
 * DirectionLeft/DirectionRight в PlayerScreen.onKeyEvent. Поэтому их
 * удаление не убирает никакой реальной функции — перемотка остаётся
 * ровно там же, где и была. Для обычного фильма транспортный кластер
 * теперь — один play/pause, без соседей, и он НЕ переезжает в центр
 * капсулы: он остаётся первым/единственным элементом в левой части
 * транспортного ряда, на том же месте, где раньше стоял между двумя
 * кнопками перемотки. Для сериала кнопки след./пред. эпизода остаются —
 * это не перемотка, отдельная функция, её не трогаем.
 *
 * Fullscreen на TV не существует как понятие — TV-приложение всегда
 * полноэкранное, эта кнопка тут не появляется (она только на телефоне,
 * см. PhonePlayerController.kt).
 *
 * Четвёртый раунд: иконка "Подключить телефон" в шапке раньше была тем же
 * decorative IconGlyphButton, что и play/pause/skip — не получала фокус
 * пульта, реальный доступ шёл только через хоткей DirectionDown в
 * PlayerScreen.onKeyEvent. Хоткей убран (конфликтовал с обычной
 * фокус-навигацией на MenuIconButton ниже — см. комментарий в
 * PlayerScreen.kt), поэтому эта иконка теперь тоже настоящий
 * фокусируемый tv-material3 Surface (переиспользует MenuIconButton, как
 * и четыре иконки настроек снизу) — иначе телефон-компаньон стал бы
 * недостижим с пульта вообще.
 *
 * Прогресс-бар: раньше статичный 4dp без какой-либо реакции на
 * перемотку — единственная обратная связь была через всплывающий текст
 * по центру экрана ("+10 сек"/название серии). Теперь бар дополнительно
 * "разбухает" до 10dp на 150ms, пока пользователь держит/повторно жмёт
 * DirectionLeft/DirectionRight (см. isSeekActive — считается в
 * PlayerScreen.kt по частоте KeyDown-событий, Android сам шлёt повторные
 * KeyDown при удержании клавиши на пульте).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    title: String = "",
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    onConnectPhone: () -> Unit = {},
    // Растёт ли сейчас прогресс-бар — управляется снаружи (PlayerScreen),
    // это единственный источник правды о том, держит ли пользователь
    // сейчас DirectionLeft/DirectionRight.
    isSeekActive: Boolean = false,
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
                    MenuIconButton(icon = Icons.Default.PhoneAndroid, contentDescription = "Подключить телефон", isActive = false, iconSize = 28.dp, onClick = onConnectPhone)
                }
            }

            // Капсула — не на весь экран, у неё собственная плашка с рамкой
            // и скруглением, а не растянутый на всю ширину градиент.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ZenithDimens.paddingXXL)
                    .widthIn(max = 860.dp)
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ZenithSurface.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = ZenithDimens.paddingM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
            ) {
                // Время слито в один ряд с баром (было: бар отдельной
                // строкой, время под ним отдельной строкой) — экономит
                // высоту капсулы на целую строку.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    ProgressBar(currentMs = currentPositionMs, durationMs = durationMs, isActive = isSeekActive, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    Text(text = formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                }

                // Один ряд: слева — транспорт (play/pause, либо
                // след./пред. эпизод по бокам для сериала), справа —
                // четыре маленькие квадратные иконки настроек
                // воспроизведения.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSeries) {
                            IconGlyphButton(
                                icon = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущая серия",
                                onClick = onPreviousEpisode,
                                enabled = hasPreviousEpisode,
                                size = 52.dp
                            )
                            Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                        }
                        IconGlyphButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                            onClick = onTogglePlay,
                            isPrimary = true,
                            size = 68.dp
                        )
                        if (isSeries) {
                            Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                            IconGlyphButton(
                                icon = Icons.Default.SkipNext,
                                contentDescription = "Следующая серия",
                                onClick = onNextEpisode,
                                enabled = hasNextEpisode,
                                size = 52.dp
                            )
                        }
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
    // нуждаются в отдельной focus/indication-логике.
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
 * Маленькие квадратные иконки (субтитры/аудио/качество/скорость внизу
 * капсулы, "Подключить телефон" наверху) — в отличие от IconGlyphButton
 * выше, это НАСТОЯЩИЙ фокусируемый TV-компонент (tv-material3 Surface),
 * потому что клик по каждой должен реально работать с пульта, не быть
 * декоративным. iconSize — из-за переиспользования для "Подключить
 * телефон" в шапке (четвёртый раунд, см. комментарий над PlayerController
 * выше), которая крупнее четырёх нижних иконок.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    iconSize: Dp = 20.dp,
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
            modifier = Modifier.padding(9.dp).size(iconSize)
        )
    }
}

@Composable
private fun ProgressBar(currentMs: Long, durationMs: Long, isActive: Boolean, modifier: Modifier = Modifier) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 4dp в покое → 10dp пока идёт перемотка (isActive выставляется
    // снаружи по частоте DirectionLeft/DirectionRight), 150ms ease-out —
    // тот же принцип, что предложен для телефона (PhonePlayerController),
    // только источник взаимодействия другой (D-pad, не касание/драг).
    val height by animateDpAsState(
        targetValue = if (isActive) 10.dp else 4.dp,
        animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
        label = "tvProgressBarHeight"
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
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
