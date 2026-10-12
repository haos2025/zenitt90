package com.platinum.ott.presentation.components

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.presentation.screens.home.isPlaylistSourced
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.ZenithDurationShort
import com.platinum.ott.ui.theme.ZenithEasingStandard
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.ui.theme.ZenithShapeSmall

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MovieCard(
        title = movie.title, poster = movie.poster, year = movie.year, onClick = onClick, modifier = modifier,
        rating = movie.rating.takeIf { it > 0.0 },
        // Логотипы из плейлистов (широкие/квадратные) вписываем целиком, настоящие постеры — по карточке.
        contentScale = if (movie.isPlaylistSourced) ContentScale.Fit else ContentScale.Crop
    )
}

/**
 * Карточка фильма. РЕДИЗАЙН: постер целиком, без постоянной чёрной плашки.
 * На TV подпись (название, год, рейтинг) показывается только в фокусе, вокруг
 * — акцентная рамка; на телефоне фокуса нет, поэтому подпись видна всегда, но
 * мягким градиентом, а не сплошной плашкой. Без постера подпись всегда видна.
 * progress (0..1) — тонкая полоса просмотра внизу ("Продолжить просмотр").
 */
@Composable
fun MovieCard(
    title: String, poster: String, year: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    rating: Double? = null, progress: Float? = null, contentScale: ContentScale = ContentScale.Crop
) {
    val cardWidth = ZenithDimens.cardWidth
    val cardHeight = ZenithDimens.cardHeight
    val context = LocalContext.current
    val density = LocalDensity.current
    val isTv = remember { isTV(context) }

    // Запрашиваем у Coil размер под физические пиксели этого экрана — важно
    // для сеток на TV, где одновременно десятки карточек.
    val widthPx = with(density) { cardWidth.roundToPx() }
    val heightPx = with(density) { cardHeight.roundToPx() }
    val request = remember(poster, widthPx, heightPx) {
        ImageRequest.Builder(context).data(poster).size(widthPx, heightPx).crossfade(true).build()
    }

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(ZenithDurationShort, easing = ZenithEasingStandard),
        label = "movieCardFocusScale"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(ZenithDurationShort, easing = ZenithEasingStandard),
        label = "movieCardFocusRing"
    )
    // ФИКС: ссылка на постер непустая, но картинка не загрузилась (404, мёртвый
    // скриншот провайдера, таймаут) — раньше на ТВ получалась пустая синяя
    // карточка без названия (подпись показывалась только в фокусе). При ошибке
    // загрузки подпись видна всегда, как для карточки без постера.
    var posterFailed by remember(poster) { mutableStateOf(false) }
    val showCaption = !isTv || isFocused || poster.isBlank() || posterFailed

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ZenithShapeSmall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                onError = { posterFailed = true },
                onSuccess = { posterFailed = false }
            )
            // Явное имя пакета: внутри Card неявный ColumnScope делал вызов
            // неоднозначным (ошибка сборки "cannot be called with an implicit receiver").
            androidx.compose.animation.AnimatedVisibility(
                visible = showCaption,
                enter = fadeIn(tween(ZenithDurationShort)),
                exit = fadeOut(tween(ZenithDurationShort)),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 10.dp)
                ) {
                    Column {
                        Text(title, color = ZenithOnSurface, style = MaterialTheme.typography.titleMedium, maxLines = if (isTv) 2 else 1, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(year.takeIf { it > 0 }?.toString(), rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
                        if (meta.isNotEmpty()) Text(meta, color = ZenithOnSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(ZenithOnSurface.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
            // Акцентная рамка фокуса — поверх всей карточки, плавно появляется.
            Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha), ZenithShapeSmall))
        }
    }
}
