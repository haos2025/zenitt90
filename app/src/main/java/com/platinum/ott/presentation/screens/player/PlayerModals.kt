package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.presentation.screens.epg.formatHm
import com.platinum.ott.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Модальные окна оверлея плеера (ТВ): список серий, список каналов, программа канала.
 * Все — внутридеревные оверлеи (как PlaybackMenuOverlay): закрываются клавишей Назад
 * (её ловит PlayerScreen), выбор — по OK. Каждое при открытии ставит фокус на текущий
 * элемент (текущая серия / текущий канал / передача в эфире).
 */

/** Какое модальное окно открыто поверх плеера. */
enum class PlayerModal { NONE, EPISODES, CHANNELS, PROGRAM }

/** Тип воспроизводимого — от него зависит, какие кнопки показывает оверлей. */
enum class PlayerKind { MOVIE, SERIES, LIVE, CATCHUP }

// ---------------------------------------------------------------------------
// Общие кирпичики
// ---------------------------------------------------------------------------

@Composable
private fun ModalFrame(
    title: String,
    subtitle: String? = null,
    widthFraction: Float = 0.62f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(0.82f)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface.copy(alpha = 0.97f))
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModalRow(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = ZenithFocusContainerActive
        ),
        // Строки впритык к краю скруглённой карточки — «pop» при фокусе вылезал бы за рамку.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ZenithOnSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Серии
// ---------------------------------------------------------------------------

private sealed interface EpItem {
    data class Header(val season: Int?) : EpItem
    data class Ep(val movie: Movie) : EpItem
}

/** episodes == null — ещё грузится. */
@Composable
fun EpisodesModal(seriesTitle: String, episodes: List<Movie>?, currentId: String?, onPick: (Movie) -> Unit) {
    ModalFrame(title = "Серии", subtitle = seriesTitle, widthFraction = 0.5f) {
        when {
            episodes == null -> CenterHint("Загрузка…")
            episodes.isEmpty() -> CenterHint("Список серий недоступен")
            else -> {
                val rows = remember(episodes) {
                    buildList<EpItem> {
                        var last: Int? = Int.MIN_VALUE
                        val multiSeason = episodes.map { it.seasonNumber }.distinct().size > 1
                        episodes.forEach { e ->
                            if (multiSeason && e.seasonNumber != last) { add(EpItem.Header(e.seasonNumber)); last = e.seasonNumber }
                            add(EpItem.Ep(e))
                        }
                    }
                }
                val listState = rememberLazyListState()
                val focus = remember { FocusRequester() }
                val currentIdx = rows.indexOfFirst { it is EpItem.Ep && it.movie.id == currentId }
                LaunchedEffect(rows) {
                    if (currentIdx >= 0) listState.scrollToItem((currentIdx - 2).coerceAtLeast(0))
                    delay(80)
                    focus.safeRequestFocus()
                }
                // Если текущей серии в списке нет — фокус на первую (requester вешаем на первую серию).
                val focusIdx = if (currentIdx >= 0) currentIdx else rows.indexOfFirst { it is EpItem.Ep }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(rows.size, key = { i ->
                        when (val it = rows[i]) { is EpItem.Header -> "h${it.season}"; is EpItem.Ep -> it.movie.id }
                    }) { i ->
                        when (val it = rows[i]) {
                            is EpItem.Header -> Text(
                                "Сезон ${it.season ?: "—"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = ZenithDimens.paddingM, top = ZenithDimens.paddingM, bottom = ZenithDimens.paddingXS)
                            )
                            is EpItem.Ep -> {
                                val isCurrent = it.movie.id == currentId
                                ModalRow(
                                    selected = isCurrent,
                                    modifier = if (i == focusIdx) Modifier.focusRequester(focus) else Modifier,
                                    onClick = { onPick(it.movie) }
                                ) {
                                    Text(
                                        (it.movie.episodeNumber?.toString() ?: "•").padStart(2, ' '),
                                        color = ZenithOnSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(
                                        it.movie.title,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else ZenithOnSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Каналы
// ---------------------------------------------------------------------------

/** channels == null — ещё грузится. Слева категории (выбор по фокусу), справа каналы. */
@Composable
fun ChannelsModal(channels: List<ChannelUiItem>?, currentId: String?, onPick: (ChannelUiItem) -> Unit) {
    ModalFrame(title = "Каналы", widthFraction = 0.7f) {
        when {
            channels == null -> CenterHint("Загрузка…")
            channels.isEmpty() -> CenterHint("Нет подписанных каналов")
            else -> {
                val allLabel = "Все каналы"
                val categories = remember(channels) {
                    listOf(allLabel) + channels.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
                }
                var selectedCategory by remember { mutableStateOf(allLabel) }
                val visible = remember(channels, selectedCategory) {
                    if (selectedCategory == allLabel) channels else channels.filter { it.category == selectedCategory }
                }
                val listState = rememberLazyListState()
                val focus = remember { FocusRequester() }
                LaunchedEffect(channels) {
                    val idx = visible.indexOfFirst { it.id == currentId }
                    if (idx >= 0) listState.scrollToItem((idx - 3).coerceAtLeast(0))
                    delay(80)
                    focus.safeRequestFocus()
                }
                val focusId = (visible.firstOrNull { it.id == currentId } ?: visible.firstOrNull())?.id
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    LazyColumn(Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(categories, key = { it }) { cat ->
                            ModalRow(
                                selected = cat == selectedCategory,
                                onFocus = { if (it) selectedCategory = cat },
                                onClick = { selectedCategory = cat }
                            ) {
                                Text(cat, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(visible, key = { it.id }) { ch ->
                            val isCurrent = ch.id == currentId
                            ModalRow(
                                selected = isCurrent,
                                modifier = if (ch.id == focusId) Modifier.focusRequester(focus) else Modifier,
                                onClick = { onPick(ch) }
                            ) {
                                Text("${ch.channelNumber}", color = ZenithOnSurface.copy(alpha = 0.5f), modifier = Modifier.width(44.dp))
                                Box(Modifier.size(34.dp).clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                    if (ch.logo != null) AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.8f))
                                }
                                Spacer(Modifier.width(ZenithDimens.paddingSM))
                                Text(
                                    ch.canonicalName,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else ZenithOnSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Программа канала
// ---------------------------------------------------------------------------

private sealed interface PgItem {
    data class Day(val label: String, val key: Long) : PgItem
    data class Prog(val p: EpgProgramEntity) : PgItem
}

private fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(ms: Long, now: Long): String {
    val diffDays = ((dayStart(ms) - dayStart(now)) / 86_400_000L).toInt()
    return when (diffDays) {
        0 -> "Сегодня"
        -1 -> "Вчера"
        1 -> "Завтра"
        else -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(ms).replaceFirstChar { it.uppercase() }
    }
}

/**
 * Программа канала. programs == null — ещё грузится. Нажатие: идущая передача — в эфир,
 * прошедшая в пределах архива — запись; будущие/вне архива только читаются (описание справа).
 */
@Composable
fun ProgramModal(
    channel: ChannelUiItem?,
    programs: List<EpgProgramEntity>?,
    nowMs: Long,
    watchingCatchup: Boolean,
    onWatchLive: () -> Unit,
    onWatchCatchup: (startMs: Long, endMs: Long) -> Unit
) {
    ModalFrame(title = "Программа", subtitle = channel?.canonicalName, widthFraction = 0.78f) {
        when {
            programs == null -> CenterHint("Загрузка…")
            programs.isEmpty() -> CenterHint("Нет данных программы передач для этого канала")
            else -> {
                val archiveMs = (channel?.catchupDaysAvailable ?: 0) * 86_400_000L
                val rows = remember(programs) {
                    buildList<PgItem> {
                        var lastDay = Long.MIN_VALUE
                        programs.sortedBy { it.startTimeMillis }.forEach { p ->
                            val d = dayStart(p.startTimeMillis)
                            if (d != lastDay) { add(PgItem.Day(dayLabel(p.startTimeMillis, nowMs), d)); lastDay = d }
                            add(PgItem.Prog(p))
                        }
                    }
                }
                val listState = rememberLazyListState()
                val focus = remember { FocusRequester() }
                val liveIdx = rows.indexOfFirst { it is PgItem.Prog && nowMs in it.p.startTimeMillis until it.p.endTimeMillis }
                val focusIdx = if (liveIdx >= 0) liveIdx else rows.indexOfFirst { it is PgItem.Prog && it.p.startTimeMillis >= nowMs }.takeIf { it >= 0 } ?: rows.indexOfLast { it is PgItem.Prog }
                var focused by remember { mutableStateOf<EpgProgramEntity?>(null) }
                LaunchedEffect(rows) {
                    if (focusIdx >= 0) listState.scrollToItem((focusIdx - 3).coerceAtLeast(0))
                    delay(80)
                    focus.safeRequestFocus()
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows.size, key = { i ->
                            when (val it = rows[i]) { is PgItem.Day -> "d${it.key}"; is PgItem.Prog -> it.p.id }
                        }) { i ->
                            when (val it = rows[i]) {
                                is PgItem.Day -> Text(
                                    it.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = ZenithDimens.paddingM, top = ZenithDimens.paddingM, bottom = ZenithDimens.paddingXS)
                                )
                                is PgItem.Prog -> {
                                    val p = it.p
                                    val isLive = nowMs in p.startTimeMillis until p.endTimeMillis
                                    val isPast = p.endTimeMillis <= nowMs
                                    val hasArchive = isPast && archiveMs > 0 && p.startTimeMillis >= nowMs - archiveMs
                                    ModalRow(
                                        selected = isLive && !watchingCatchup,
                                        modifier = if (i == focusIdx) Modifier.focusRequester(focus) else Modifier,
                                        onFocus = { f -> if (f) focused = p },
                                        onClick = {
                                            when {
                                                isLive -> onWatchLive()
                                                hasArchive -> onWatchCatchup(p.startTimeMillis, p.endTimeMillis)
                                            }
                                        }
                                    ) {
                                        Text("${formatHm(p.startTimeMillis)}", color = ZenithOnSurface.copy(alpha = 0.5f), modifier = Modifier.width(58.dp))
                                        Text(
                                            p.title,
                                            color = when {
                                                isLive -> MaterialTheme.colorScheme.primary
                                                isPast && !hasArchive -> ZenithOnSurface.copy(alpha = 0.4f)
                                                else -> ZenithOnSurface
                                            },
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                        )
                                        when {
                                            isLive -> Text("В ЭФИРЕ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            hasArchive -> Text("запись", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.55f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Описание сфокусированной передачи.
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        val p = focused
                        if (p == null) {
                            Text("Выберите передачу", color = ZenithOnSurface.copy(alpha = 0.5f))
                        } else {
                            Text(p.title, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatHm(p.startTimeMillis)}–${formatHm(p.endTimeMillis)}" + (p.category?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.6f)
                            )
                            p.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f), maxLines = 9, overflow = TextOverflow.Ellipsis)
                            }
                            val isLive = nowMs in p.startTimeMillis until p.endTimeMillis
                            val isPast = p.endTimeMillis <= nowMs
                            val hasArchive = isPast && archiveMs > 0 && p.startTimeMillis >= nowMs - archiveMs
                            Text(
                                when {
                                    isLive -> "OK — смотреть в эфире"
                                    hasArchive -> "OK — смотреть запись"
                                    isPast -> "Запись этой передачи недоступна"
                                    else -> "Передача ещё не началась"
                                },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
