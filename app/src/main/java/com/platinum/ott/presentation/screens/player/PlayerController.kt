package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.ZenithSurface

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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ZenithDimens.paddingXXL)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(0.62f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ZenithSurface.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = ZenithDimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                ProgressBar(currentMs = currentPositionMs, durationMs = durationMs)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconGlyphButton(
                        icon = if (isSeries) Icons.Default.SkipPrevious else Icons.Default.Replay10,
                        contentDescription = if (isSeries) "Предыдущая серия" else "-10 секунд",
                        onClick = if (isSeries) onPreviousEpisode else onSeekBackward,
                        enabled = !isSeries || hasPreviousEpisode,
                        size = 52.dp
                    )
                    Spacer(modifier = Modifier.width(ZenithDimens.paddingXL))
                    IconGlyphButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                        onClick = onTogglePlay,
                        isPrimary = true,
                        size = 68.dp
                    )
                    Spacer(modifier = Modifier.width(ZenithDimens.paddingXL))
                    IconGlyphButton(
                        icon = if (isSeries) Icons.Default.SkipNext else Icons.Default.Forward10,
                        contentDescription = if (isSeries) "Следующая серия" else "+10 секунд",
                        onClick = if (isSeries) onNextEpisode else onSeekForward,
                        enabled = !isSeries || hasNextEpisode,
                        size = 52.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun IconGlyphButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    // Box+clickable, не Surface — D-pad уже обрабатывается глобально в
    // PlayerScreen.onKeyEvent, эти кнопки не получают фокус напрямую и не
    // нуждаются в отдельной focus/indication-логике (то же решение, что
    // было в старой ControlButton).
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
