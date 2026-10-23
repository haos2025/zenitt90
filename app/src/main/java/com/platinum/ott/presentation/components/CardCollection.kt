package com.platinum.ott.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.CardStyle
import com.platinum.ott.ui.theme.LocalAppearance
import com.platinum.ott.ui.theme.TvLayout
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithShapeSmall
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithSurfaceVariant
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/**
 * Контейнер для карточек раздела (план 11): форму выбирает пользователь в «Внешний вид».
 * - «Список» → [listItem] в колонке;
 * - «Постер» / «Широкая» → [cardItem] (wide = true для широкой) в сетке с адаптивной шириной
 *   ячейки (учитывает «Размер элементов»), а на ТВ при раскладке «Лента» — в горизонтальной ленте.
 * Общий для ТВ и телефона, поэтому без androidx.tv.material3.
 */
@Composable
fun <T> CardCollection(
    items: List<T>,
    key: (T) -> Any,
    section: CardSection,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalGap: Dp = ZenithDimens.paddingSM,
    verticalGap: Dp = ZenithDimens.paddingM,
    listItem: @Composable (item: T, index: Int) -> Unit,
    cardItem: @Composable (item: T, index: Int, wide: Boolean) -> Unit
) {
    val appearance = LocalAppearance.current
    val style = appearance.styleOf(section, isTv)
    if (style == CardStyle.LIST) {
        LazyColumn(modifier, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item -> listItem(item, index) }
        }
        return
    }
    val wide = style == CardStyle.WIDE
    val posterWidth = ZenithDimens.cardWidth
    val posterHeight = ZenithDimens.cardHeight
    // Те же размеры, что у MovieCard(wide = true): высота 0.7 от постера, ширина 16:9.
    val cellMin = if (wide) posterHeight * 0.7f * (16f / 9f) else posterWidth
    if (isTv && appearance.tvLayout == TvLayout.CAROUSEL) {
        LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item -> cardItem(item, index, wide) }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cellMin),
            modifier = modifier,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            verticalArrangement = Arrangement.spacedBy(verticalGap)
        ) {
            gridItemsIndexed(items, key = { _, item -> key(item) }) { index, item -> cardItem(item, index, wide) }
        }
    }
}

/**
 * Строка списка: миниатюра + название + подпись (+ полоса прогресса). [trailing] — отдельная
 * фокусируемая кнопка справа (действия/удаление), чтобы не вкладывать кликабельное в кликабельное.
 */
@Composable
fun PosterListRow(
    title: String,
    poster: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    thumbWidth: Dp = 64.dp,
    trailing: @Composable () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbHeight = thumbWidth * 1.5f
    val widthPx = with(density) { thumbWidth.roundToPx() }
    val heightPx = with(density) { thumbHeight.roundToPx() }
    val request = remember(poster, widthPx, heightPx) {
        ImageRequest.Builder(context).data(poster).size(widthPx, heightPx).crossfade(true).build()
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Card(
            onClick = onClick,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.hasFocus },
            shape = ZenithShapeMedium,
            colors = CardDefaults.cardColors(containerColor = ZenithSurfaceVariant.copy(alpha = 0.5f)),
            border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(Modifier.padding(ZenithDimens.paddingSM), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.width(thumbWidth).height(thumbHeight).clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surface)
                )
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                Column(Modifier.weight(1f)) {
                    Text(title, color = ZenithOnSurface, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (progress != null && progress > 0f) {
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS))
                    }
                }
            }
        }
        trailing()
    }
}
