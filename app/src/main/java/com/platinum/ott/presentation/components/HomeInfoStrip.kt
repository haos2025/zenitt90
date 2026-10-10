package com.platinum.ott.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.ui.theme.ZenithDurationShort
import com.platinum.ott.ui.theme.ZenithEasingStandard
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithShapeSmall

/**
 * ФИКС/РЕДИЗАЙН (по запросу Shadow): замена HeroBanner. HeroBanner был
 * декоративной ротацией случайных карточек backend-каталога и пустовал,
 * когда нечего было ротировать, либо просто отвлекал местом на экране от
 * контента — Shadow явно попросил убрать. Вместо декоративного — полезное:
 * одна тонкая строка вместо баннера в четверть экрана, показывает только
 * то, что реально можно продолжить или на что стоит обратить внимание
 * прямо сейчас; если нечего показывать — не рендерит вообще ничего (в
 * отличие от HeroBanner, который в таком случае просто не появлялся, но
 * место под него в LazyColumn.item все равно было отдельной строкой кода
 * с проверкой isNotEmpty — здесь та же идея, проверка на уровне caller'а
 * в HomeScreen.kt/PhoneHomeScreen.kt).
 *
 * Общий компонент для TV и телефона — как и MovieCard.kt (см. его же
 * комментарий про PROMPT_DESIGN_SYSTEM.md), androidx.tv.material3.Surface
 * здесь использовать нельзя (телефон рендерит этот же компонент), поэтому
 * обычный Card + ручной scale/подсветка при фокусе — тот же паттерн.
 *
 * Не дублирует ContinueWatchingRow (полный список серий/фильмов ниже по
 * ленте) — здесь только САМАЯ свежая запись, как быстрый доступ в один тап,
 * без прокрутки. Блок "Новая серия" — новая функциональность, которой на
 * Home раньше не было вообще (расписание уже собиралось для push-
 * уведомлений через SeriesTrackerUseCase/SeriesUpdateWorker, но никак не
 * использовалось на самом экране).
 */
@Composable
fun HomeInfoStrip(
    continueWatching: WatchHistoryEntity?,
    recentlyAired: SeriesScheduleEntity?,
    onContinueClick: (String) -> Unit,
    onNewEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (continueWatching == null && recentlyAired == null) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
    ) {
        if (continueWatching != null) {
            ContinueWatchingCell(
                entry = continueWatching,
                onClick = { onContinueClick(continueWatching.contentId) },
                modifier = Modifier.weight(1f)
            )
        }
        if (recentlyAired != null) {
            NewEpisodeCell(
                schedule = recentlyAired,
                onClick = { onNewEpisodeClick(recentlyAired.seriesId) },
                // Если "Продолжить" тоже показан — у "Новой серии" фиксированная
                // ширина поменьше (это вторичный по важности блок); если это
                // единственный блок в строке — растягивается на всю ширину.
                modifier = if (continueWatching != null) Modifier.width(260.dp) else Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContinueWatchingCell(entry: WatchHistoryEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(ZenithDurationShort, easing = ZenithEasingStandard),
        label = "infoStripContinueScale"
    )
    Card(
        onClick = onClick,
        modifier = modifier.height(88.dp).scale(scale).onFocusChanged { isFocused = it.isFocused },
        shape = ZenithShapeSmall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(entry.poster).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.width(52.dp).fillMaxHeight().clip(ZenithShapeSmall)
                )
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Column(Modifier.weight(1f)) {
                    Text("Продолжить просмотр", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            // Тот же паттерн подсветки, что в MovieCard.kt — рисуется
            // последним, поверх постера и текста.
            if (isFocused) Box(Modifier.fillMaxSize().background(ZenithFocusContainerActive))
        }
    }
}

@Composable
private fun NewEpisodeCell(schedule: SeriesScheduleEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(ZenithDurationShort, easing = ZenithEasingStandard),
        label = "infoStripNewEpisodeScale"
    )
    Card(
        onClick = onClick,
        modifier = modifier.height(88.dp).scale(scale).onFocusChanged { isFocused = it.isFocused },
        shape = ZenithShapeSmall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(ZenithDimens.paddingS), verticalArrangement = Arrangement.Center) {
                Text("Новая серия", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(schedule.seriesName, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Сезон ${schedule.seasonNum}, серия ${schedule.episodeNum}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isFocused) Box(Modifier.fillMaxSize().background(ZenithFocusContainerActive))
        }
    }
}
