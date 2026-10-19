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
import kotlin.math.abs
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.presentation.screens.home.isPlaylistSourced
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.ZenithDurationShort
import com.platinum.ott.ui.theme.ZenithEasingStandard
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.ui.theme.ZenithShapeSmall

/**
 * Для записей истории/избранного есть только contentId (Movie нет): настоящий постер бывает лишь
 * у контента с бэкенда (yt_/ia_) — его кадрируем; всё остальное (логотипы каналов, картинки
 * из плейлистов) вписываем целиком, иначе логотип растягивается на всю карточку.
 */
fun contentScaleForId(contentId: String): ContentScale =
    if (contentId.startsWith("ch_")) ContentScale.Fit else ContentScale.Crop

/**
 * Серия/фильм из собственного плейлиста (не канал): у такого контента кадр почти всегда широкий
 * (скриншот 16:9), поэтому карточка для него — горизонтальная (wide), а не высокий постер 2:3.
 */
fun isPlaylistVodId(id: String): Boolean =
    !id.startsWith("ch_") && (id.startsWith("m3u_") || id.contains("_m3u_") || id.startsWith("xt_") || id.contains("_xt_"))

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MovieCard(
        title = movie.title, poster = movie.poster, year = movie.year, onClick = onClick, modifier = modifier,
        rating = movie.rating.takeIf { it > 0.0 },
        // Логотипы из плейлистов (широкие/квадратные) вписываем целиком, настоящие постеры — по карточке.
        contentScale = if (movie.isPlaylistSourced) ContentScale.Fit else ContentScale.Crop,
        // Для контента плейлиста способ вписывания выбирается по РЕАЛЬНОЙ картинке (см. autoScale).
        autoScale = isPlaylistVodId(movie.id),
        // Поля нужны только логотипам каналов (ch_).
        logoPadding = movie.id.startsWith("ch_")
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
    rating: Double? = null, progress: Float? = null, contentScale: ContentScale = ContentScale.Crop,
    logoPadding: Boolean = true,
    // wide — горизонтальная карточка 16:9 (для рядов плейлиста): ниже обычной, но шире.
    wide: Boolean = false,
    // autoScale — Crop, если форма картинки близка к форме карточки (теряется немного), иначе Fit
    // с фоном по бокам/сверху-снизу: постер или логотип не обрезается, широкий кадр заполняет wide-карточку.
    autoScale: Boolean = false
) {
    val baseHeight = ZenithDimens.cardHeight
    val cardHeight = if (wide) baseHeight * 0.7f else baseHeight
    val cardWidth = if (wide) cardHeight * (16f / 9f) else ZenithDimens.cardWidth
    val cardAspect = cardWidth.value / cardHeight.value
    var imageAspect by remember(poster) { mutableStateOf<Float?>(null) }
    val effectiveScale = if (autoScale) {
        val a = imageAspect
        if (a == null || abs(a / cardAspect - 1f) < 0.4f) ContentScale.Crop else ContentScale.Fit
    } else contentScale
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
                contentScale = effectiveScale,
                // Логотипы (Fit) — с полями, чтобы не упирались в края и не были огромными.
                modifier = if (effectiveScale == ContentScale.Fit) Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)).let { if (logoPadding) it.padding(horizontal = 28.dp, vertical = 44.dp) else it } else Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                onError = { posterFailed = true },
                onSuccess = {
                    posterFailed = false
                    val d = it.result.drawable
                    if (autoScale && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        imageAspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight
                    }
                }
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
