package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.platinum.ott.domain.model.CastMember

private val photoWidth = 84.dp
private val photoHeight = 110.dp

/**
 * Горизонтальный ряд карточек актёров (фото + имя + роль).
 * PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4. Не показывается вызывающей
 * стороной вообще, если members пуст (см. DetailScreen.kt/PhoneDetailScreen.kt) —
 * сам компонент этого не решает, чтобы не плодить "пустой заголовок секции".
 */
@Composable
fun CastRow(members: List<CastMember>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { photoWidth.roundToPx() }
    val heightPx = with(density) { photoHeight.roundToPx() }

    Column(modifier = modifier) {
        Text("Актёры", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
            items(members, key = { it.name + it.character.orEmpty() }) { member ->
                val request = remember(member.profilePath, widthPx, heightPx) {
                    TmdbImage.profileUrl(member.profilePath, widthPx)?.let {
                        ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build()
                    }
                }
                Column(modifier = Modifier.width(photoWidth)) {
                    Box(
                        modifier = Modifier.width(photoWidth).height(photoHeight)
                            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (request != null) {
                            AsyncImage(
                                model = request, contentDescription = member.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            // Плейсхолдер вместо пустой дырки в ряду — тот же
                            // принцип, что уже применён для фильмов без постера.
                            Icon(Icons.Default.Person, contentDescription = member.name, tint = Color.Gray,
                                modifier = Modifier.align(Alignment.Center).size(36.dp))
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingXS))
                    Text(member.name, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    member.character?.let {
                        Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
