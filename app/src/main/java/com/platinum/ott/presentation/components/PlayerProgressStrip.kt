package com.platinum.ott.presentation.components

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Тонкая полоска прогресса воспроизведения (общая для TV и телефона, без
 * material-зависимостей — цвет передаётся снаружи). Показывается, пока
 * основная капсула управления спрятана. bufferedMs > positionMs рисуется
 * светлой подложкой между фоном и пройденной частью (0 — не рисовать).
 * overrideFraction — позиция "перетаскивания" на телефоне (вместо positionMs).
 */
@Composable
fun PlayerProgressStrip(
    positionMs: Long,
    durationMs: Long,
    thickness: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
    overrideFraction: Float? = null
) {
    val played = overrideFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(thickness).background(ZenithOnSurface.copy(alpha = 0.25f))) {
        if (buffered > played) Box(Modifier.fillMaxWidth(buffered).fillMaxHeight().background(ZenithOnSurface.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxWidth(played).fillMaxHeight().background(color))
    }
}
