package com.platinum.ott.presentation.phone.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum.ott.presentation.components.PlayerProgressStrip
import com.platinum.ott.ui.theme.ZenithShapeSmall

/**
 * Перетаскиваемая тонкая полоска прогресса для телефона (показывается, пока
 * основная капсула спрятана). Зона касания — нижние 28dp экрана (не весь
 * экран, чтобы не конфликтовать со свайпами яркости/громкости/перемотки в
 * PhonePlayerScreen). Во время перетаскивания полоска утолщается, над ней
 * всплывает плашка "12:30 / 45:10" + название; сама перемотка применяется
 * один раз, при отпускании пальца (как у Slider в капсуле).
 * Вариант "А" по решению Shadow: без картинок-миниатюр, только время.
 */
@Composable
fun PhoneScrubStrip(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    thicknessDp: Int,
    title: String,
    onInteraction: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val thickness by animateDpAsState(if (dragFraction != null) (thicknessDp + 5).dp else thicknessDp.dp, label = "scrubStripThickness")

    // Внешняя колонка растёт вверх (родитель выравнивает по низу), когда
    // над полоской появляется плашка; зона касания — только нижние 28dp.
    // Системные жесты (Назад с левого/правого края, смена приложения по
    // "полоске" внизу) забирают касания у краёв экрана — зона перетаскивания
    // отступает от этих зон на размер системных жест-инсетов.
    Column(
        modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        dragFraction?.let { f ->
            Column(
                Modifier.padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), ZenithShapeSmall)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .widthIn(max = 240.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${formatScrubTime((f * durationMs).toLong())} / ${formatScrubTime(durationMs)}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                if (title.isNotBlank()) Text(title, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onInteraction()
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeekTo((it * durationMs).toLong().coerceIn(0L, durationMs)) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onInteraction()
                        }
                    )
                }
        ) {
            PlayerProgressStrip(
                positionMs = positionMs,
                durationMs = durationMs,
                thickness = thickness,
                color = MaterialTheme.colorScheme.primary,
                bufferedMs = bufferedMs,
                overrideFraction = dragFraction,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private fun formatScrubTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
