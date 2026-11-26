package com.platinum.ott.presentation.components

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
fun PersonFilmographyRow(
    title: String,
    items: List<PersonCreditItem>,
    onItemClick: (PersonCreditItem) -> Unit = {},
    modifier: Modifier = Modifier,
    focusState: RowFocusState = rememberRowFocusState(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { posterWidth.roundToPx() }
    val heightPx = with(density) { posterHeight.roundToPx() }

    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, modifier = Modifier.padding(start = RowContentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)))
        Spacer(Modifier.height(ZenithDimens.paddingS))
        LazyRow(
            state = focusState.listState,
            contentPadding = RowContentPadding,
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM),
            modifier = Modifier.rowEntry(focusState)
        ) {
            itemsIndexed(items, key = { i, c -> "${i}_${c.tmdbId}${c.mediaType}" }) { index, credit ->
                val request = remember(credit.posterPath, widthPx, heightPx) {
                    TmdbImage.posterUrl(credit.posterPath, widthPx)?.let {
                        ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build()
                    }
                }
                FocusRowCard(
                    width = posterWidth, imageHeight = posterHeight, state = focusState, index = index,
                    onClick = { onItemClick(credit) },
                    image = {
                        if (request != null) {
                            AsyncImage(
                                model = request, contentDescription = credit.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    },
                    caption = { focused ->
                        Text(credit.title, color = if (focused) ZenithOnSurface else ZenithOnSurface.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        credit.character?.let {
                            Text(it, color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                )
            }
        }
    }
}
