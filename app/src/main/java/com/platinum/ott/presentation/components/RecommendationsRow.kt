package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Recommendation

private val posterWidth = 110.dp
private val posterHeight = 165.dp

/**
 * «Смотрите также» — PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5. Вызывающая
 * сторона не рендерит компонент вовсе, если items пуст или tmdbId фильма
 * неизвестен (см. DetailScreen.kt/PhoneDetailScreen.kt).
 *
 * Клик (onItemClick) — Recommendation - запись из TMDB, необязательно
 * присутствующая в собственном каталоге приложения, прямого movieId нет —
 * вызывающая сторона (DetailViewModel.findInCatalog()) ищет совпадение по
 * названию через тот же SearchMoviesUseCase, что и обычный поиск, и либо
 * переходит на найденную карточку, либо сообщает, что не нашлось.
 *
 * Докрутка ВСЕГО экрана к этому ряду (когда он изначально не виден) — на
 * стороне DetailScreen.kt (TvLazyColumn), не здесь; более ранняя версия
 * этого файла пыталась решить это через BringIntoViewRequester прямо тут —
 * не помогло (см. разбор в DetailScreen.kt), убрано за ненадобностью при
 * переходе на TvLazyColumn.
 */
@Composable
fun RecommendationsRow(items: List<Recommendation>, onItemClick: (Recommendation) -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { posterWidth.roundToPx() }
    val heightPx = with(density) { posterHeight.roundToPx() }

    Column(modifier = modifier) {
        Text("Смотрите также", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
            items(items, key = { it.tmdbId }) { rec ->
                val request = remember(rec.posterPath, widthPx, heightPx) {
                    TmdbImage.posterUrl(rec.posterPath, widthPx)?.let {
                        ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build()
                    }
                }
                var isFocused by remember { mutableStateOf(false) }
                Column(modifier = Modifier.width(posterWidth)) {
                    Box(
                        modifier = Modifier.width(posterWidth).height(posterHeight)
                            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(if (isFocused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { onItemClick(rec) }
                    ) {
                        if (request != null) {
                            AsyncImage(
                                model = request, contentDescription = rec.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingXS))
                    Text(rec.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
