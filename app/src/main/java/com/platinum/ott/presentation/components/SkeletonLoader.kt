package com.platinum.ott.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens

// Раньше загрузка каталога (и на TV, и на телефоне) показывала только
// CircularProgressIndicator посреди пустого экрана — ни намёка на форму
// будущего контента. Один файл на обе платформы: заглушки — просто
// Box+shimmer без единого interactive-элемента, ни tv-material3, ни
// обычный material3 им не нужны вообще.
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val translate by transition.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "skeleton-translate"
    )
    val base = Color.White.copy(alpha = 0.06f)
    val highlight = Color.White.copy(alpha = 0.16f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f)
    )
}

@Composable
private fun SkeletonBox(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(8.dp)).background(rememberShimmerBrush()))
}

/**
 * Имитация каталога (заголовок + ряд карточек, несколько раз) на время
 * первой загрузки. cardWidth/cardHeight — те же адаптивные токены
 * (ZenithDimens), что и у настоящих MovieCard, чтобы при появлении
 * реального контента карточки не "прыгали" в размере.
 */
@Composable
fun SkeletonCatalog(rows: Int = 4, cardsPerRow: Int = 5, modifier: Modifier = Modifier) {
    val cardWidth = ZenithDimens.cardWidth
    val cardHeight = ZenithDimens.cardHeight
    Column(modifier.fillMaxSize().padding(ZenithDimens.paddingM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
        repeat(rows) {
            Column(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                SkeletonBox(width = 160.dp, height = 22.dp) // заголовок ряда
                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                    repeat(cardsPerRow) { SkeletonBox(width = cardWidth, height = cardHeight) }
                }
            }
        }
    }
}
