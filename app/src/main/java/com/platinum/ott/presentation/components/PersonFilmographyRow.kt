package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.platinum.ott.domain.model.PersonCreditItem
import com.platinum.ott.ui.theme.ZenithShapeSmall

private val posterWidth = 110.dp
private val posterHeight = 165.dp

/**
 * Секция фильмографии на экране актёра (PersonDetailScreen.kt/
 * PhonePersonDetailScreen.kt) — "Фильмы" и "Сериалы" отдельными
 * вызовами (media_type у TMDB combined_credits один список на оба типа,
 * разделение — на стороне вызывающего экрана, не здесь). Тот же паттерн
 * клика/фокуса, что и у RecommendationsRow.kt: PersonCreditItem — запись
 * TMDB, необязательно присутствующая в собственном каталоге приложения,
 * прямого movieId нет — вызывающая сторона (PersonDetailViewModel.findInCatalog())
 * ищет совпадение по названию и либо переходит, либо сообщает, что не
 * нашлось. Не показывается вызывающей стороной вовсе, если items пуст.
 */
@Composable
fun PersonFilmographyRow(title: String, items: List<PersonCreditItem>, onItemClick: (PersonCreditItem) -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { posterWidth.roundToPx() }
    val heightPx = with(density) { posterHeight.roundToPx() }

    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
            items(items, key = { it.tmdbId.toString() + it.mediaType }) { credit ->
                val request = remember(credit.posterPath, widthPx, heightPx) {
                    TmdbImage.posterUrl(credit.posterPath, widthPx)?.let {
                        ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build()
                    }
                }
                var isFocused by remember { mutableStateOf(false) }
                Column(modifier = Modifier.width(posterWidth)) {
                    Box(
                        modifier = Modifier.width(posterWidth).height(posterHeight)
                            .clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surface)
                            .border(if (isFocused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, ZenithShapeSmall)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { onItemClick(credit) }
                    ) {
                        if (request != null) {
                            AsyncImage(
                                model = request, contentDescription = credit.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingXS))
                    Text(credit.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    credit.character?.let {
                        Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
