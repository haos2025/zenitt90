package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithShapeSmall

/** Список каналов поверх плеера (телефон): поиск, текущий канал подсвечен и сразу виден. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneChannelsSheet(
    channels: List<ChannelUiItem>?,
    currentId: String,
    nowPlaying: suspend (String) -> Pair<String, Float>? = { null },
    onPick: (ChannelUiItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM)) {
            Text("Каналы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = ZenithDimens.paddingS))
            if (channels == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            var query by remember { mutableStateOf("") }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(MaterialTheme.colorScheme.onSurface, 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Поиск канала", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    inner()
                }
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            val list = remember(channels, query) {
                channels.sortedBy { it.channelNumber }.filter { query.isBlank() || it.canonicalName.contains(query, ignoreCase = true) }
            }
            val startIndex = remember(list) { list.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                itemsIndexed(list, key = { i, c -> "${i}_${c.id}" }) { _, ch ->
                    val current = ch.id == currentId
                    Row(
                        Modifier.fillMaxWidth().clip(ZenithShapeSmall)
                            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(ch) }
                            .padding(horizontal = ZenithDimens.paddingS, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${ch.channelNumber}", color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                        Box(Modifier.size(40.dp).clip(CircleShape).background(ZenithOnSurface.copy(alpha = 0.08f)), Alignment.Center) {
                            if (ch.logo != null) AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.75f))
                            else Text(ch.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(ZenithDimens.paddingM))
                        // Текущая передача: грузится только для показанных строк, обновляется раз в минуту.
                        var now by remember(ch.id) { mutableStateOf<Pair<String, Float>?>(null) }
                        LaunchedEffect(ch.id) {
                            while (true) {
                                now = nowPlaying(ch.id)
                                kotlinx.coroutines.delay(60_000)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.canonicalName,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val program = now
                            if (program != null) {
                                Text(program.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { program.second }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Список серий сериала поверх плеера (телефон). Текущая серия подсвечена и сразу видна. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneEpisodesSheet(
    seriesTitle: String,
    episodes: List<Movie>?,
    currentId: String,
    onPick: (Movie) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM)) {
            Text("Серии", style = MaterialTheme.typography.titleMedium)
            Text(seriesTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            if (episodes == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val startIndex = remember(episodes) { episodes.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                itemsIndexed(episodes, key = { i, e -> "${i}_${e.id}" }) { _, ep ->
                    val current = ep.id == currentId
                    val label = buildString {
                        if (ep.seasonNumber != null && ep.episodeNumber != null) append("S${ep.seasonNumber} · E${ep.episodeNumber}  ")
                        append(ep.title)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(ZenithShapeSmall)
                            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(ep) }
                            .padding(horizontal = ZenithDimens.paddingS, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
