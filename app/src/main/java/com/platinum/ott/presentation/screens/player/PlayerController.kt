package com.platinum.ott.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.delay

/**
 * Кастомный контроллер плеера для Android TV.
 *
 * Возможности:
 *   — Перемотка на 10 секунд кнопками D-pad влево/вправо
 *   — Пауза/воспроизведение кнопкой OK (Center)
 *   — Прогресс-бар с текущей позицией и длительностью
 *   — Автоскрытие через 3 секунды без действий
 *   — Показывается при любом нажатии кнопки на пульте
 *
 * Принимает колбэки от PlayerScreen, который уже перехватывает KeyEvent.
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
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn(),
        exit    = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Градиент снизу для читаемости контроллера
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Элементы управления
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Прогресс-бар
                ProgressBar(
                    currentMs  = currentPositionMs,
                    durationMs = durationMs
                )

                // Время
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = formatTime(currentPositionMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text  = formatTime(durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Кнопки управления по центру
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // -10 секунд
                    ControlButton(
                        label   = "◀◀  10с",
                        onClick = onSeekBackward
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Пауза / воспроизведение
                    ControlButton(
                        label    = if (isPlaying) "⏸" else "▶",
                        onClick  = onTogglePlay,
                        isPrimary = true
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // +10 секунд
                    ControlButton(
                        label   = "10с  ▶▶",
                        onClick = onSeekForward
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false
) {
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (isPrimary) 50 else 8)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (isPrimary)
                Color(0xFF6C63FF).copy(alpha = 0.85f)
            else
                Color.White.copy(alpha = 0.12f),
            focusedContainerColor = if (isPrimary)
                Color(0xFF6C63FF)
            else
                Color.White.copy(alpha = 0.25f)
        ),
        modifier = if (isPrimary)
            Modifier.size(64.dp)
        else
            Modifier.height(48.dp).widthIn(min = 96.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ProgressBar(
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(Color(0xFF6C63FF), RoundedCornerShape(2.dp))
        )
    }
}

/** Форматирует миллисекунды в MM:SS или HH:MM:SS */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
