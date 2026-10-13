package com.platinum.ott.presentation.screens.player

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.*

/**
 * Контроллер плеера для Android TV.
 *
 * Третий раунд (убраны декоративные ±10с): кнопки ±10с в транспортном
 * кластере НИКОГДА не были реально интерактивными на TV — они не получают
 * фокус пульта (см. IconGlyphButton ниже, clickable без индикации focus),
 * настоящая перемотка всегда шла мимо них, через глобальный
 * DirectionLeft/DirectionRight в PlayerScreen.onKeyEvent. Поэтому их
 * удаление не убирает никакой реальной функции — перемотка остаётся
 * ровно там же, где и была. Для обычного фильма транспортный кластер
 * теперь — один play/pause, без соседей, и он НЕ переезжает в центр
 * капсулы: он остаётся первым/единственным элементом в левой части
 * транспортного ряда, на том же месте, где раньше стоял между двумя
 * кнопками перемотки. Для сериала кнопки след./пред. эпизода остаются —
 * это не перемотка, отдельная функция, её не трогаем.
 *
 * Fullscreen на TV не существует как понятие — TV-приложение всегда
 * полноэкранное, эта кнопка тут не появляется (она только на телефоне,
 * см. PhonePlayerController.kt).
 *
 * Четвёртый раунд: иконка "Подключить телефон" в шапке раньше была тем же
 * decorative IconGlyphButton, что и play/pause/skip — не получала фокус
 * пульта, реальный доступ шёл только через хоткей DirectionDown в
 * PlayerScreen.onKeyEvent. Хоткей убран (конфликтовал с обычной
 * фокус-навигацией на MenuIconButton ниже — см. комментарий в
 * PlayerScreen.kt), поэтому эта иконка теперь тоже настоящий
 * фокусируемый tv-material3 Surface (переиспользует MenuIconButton, как
 * и четыре иконки настроек снизу) — иначе телефон-компаньон стал бы
 * недостижим с пульта вообще.
 *
 * Прогресс-бар: раньше статичный 4dp без какой-либо реакции на
 * перемотку — единственная обратная связь была через всплывающий текст
 * по центру экрана ("+10 сек"/название серии). Теперь бар дополнительно
 * "разбухает" до 10dp на 150ms, пока пользователь держит/повторно жмёт
 * DirectionLeft/DirectionRight (см. isSeekActive — считается в
 * PlayerScreen.kt по частоте KeyDown-событий, Android сам шлёt повторные
 * KeyDown при удержании клавиши на пульте).
 *
 * Пятый раунд (сравнение с TiviMate/Netflix/YouTube TV, реальный репорт —
 * кнопки транспорта выглядят как настоящие, но не выделяются пультом):
 * IconGlyphButton (play/pause, след./пред. серия) до этого раунда был тем
 * же decorative-паттерном, что чинили в четвёртом раунде для "Подключить
 * телефон" — .focusProperties { canFocus = false }, реальное действие
 * только через скрытые глобальные хоткеи. Переведён на тот же
 * tv-material3 Surface, что и MenuIconButton — теперь весь ряд транспорта
 * реально фокусируется и подсвечивается белой рамкой, а не только четыре
 * иконки настроек справа. Глобальные хоткеи (DirectionCenter/Left/Right в
 * PlayerScreen.onKeyEvent) не убраны — они по-прежнему нужны, пока фокус
 * стоит на самом видео (rootHasFocus), а не в капсуле; как только
 * пользователь явно переводит фокус на кнопку в капсуле, Left/Right
 * закономерно начинают перемещать фокус между кнопками ряда, а не
 * перематывать — это соответствует поведению конкурентов, не регрессия.
 */
/**
 * ПЕРЕДЕЛАНО (двухуровневая модель, выбрана Shadow): оверлей не «режим фокуса», а два уровня
 * в одной вертикали, по которой ходят Вверх/Вниз:
 *   level 1 — фокус на видео: полоса прогресса «активна», Влево/Вправо = перемотка;
 *   level 2 — фокус в ряду кнопок: Влево/Вправо ходят по кнопкам.
 * Вверх с уровня 2 → уровень 1, Вверх с уровня 1 → скрыть, Назад — на ряд выше, затем скрыть,
 * затем выйти (всё это — в PlayerScreen.onKeyEvent). Содержимое зависит от kind:
 *   MOVIE   — play/pause + настройки;
 *   SERIES  — ⏮ ⏯ ⏭, «Серии» + настройки;
 *   LIVE    — «в эфире сейчас/далее» вместо полосы, «Каналы», «Программа» + настройки (без скорости);
 *   CATCHUP — полоса записи, «В эфир», «Каналы», «Программа» + настройки.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerController(
    isVisible: Boolean,
    level: Int,
    kind: PlayerKind,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    primaryFocusRequester: FocusRequester,
    title: String = "",
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    liveInfo: ZapBannerInfo? = null,
    onOpenEpisodes: () -> Unit = {},
    onOpenChannels: () -> Unit = {},
    onOpenProgram: () -> Unit = {},
    onWatchLive: () -> Unit = {},
    // Растёт ли сейчас прогресс-бар — управляется снаружи (PlayerScreen).
    isSeekActive: Boolean = false,
    // Прогруженная вперёд позиция (0 — не показывать: выключено в настройках или live).
    bufferedPositionMs: Long = 0L,
    subtitlesEnabled: Boolean = false,
    playbackSpeed: Float = 1f,
    onOpenMenuTab: (PlaybackMenuTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLive = kind == PlayerKind.LIVE
    val isSeries = kind == PlayerKind.SERIES

    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        exit    = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Шапка: название + метка типа.
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.2f).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            )
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .padding(horizontal = ZenithDimens.paddingXXL, vertical = ZenithDimens.paddingXL),
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (kind) {
                    PlayerKind.LIVE -> KindBadge("● ЭФИР", ZenithError)
                    PlayerKind.CATCHUP -> KindBadge("ЗАПИСЬ", MaterialTheme.colorScheme.secondary)
                    else -> {}
                }
                if (title.isNotBlank()) Text(text = title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ZenithDimens.paddingXL)
                    .widthIn(max = 900.dp)
                    .fillMaxWidth(0.82f)
                    .clip(ZenithShapeLarge)
                    .background(ZenithSurface.copy(alpha = 0.93f))
                    .border(1.dp, if (level == 1) ZenithOnSurface.copy(alpha = 0.16f) else ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeLarge)
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = ZenithDimens.paddingM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                // Ряд 1: полоса прогресса (VOD/запись) или «сейчас в эфире» (live).
                if (isLive) {
                    LiveNowNext(liveInfo)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.7f))
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        ProgressBar(currentMs = currentPositionMs, bufferedMs = bufferedPositionMs, durationMs = durationMs, isActive = isSeekActive, emphasized = level == 1, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        Text(text = formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.4f))
                    }
                }

                // Ряд 2: кнопки.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                        if (isSeries) {
                            IconGlyphButton(Icons.Default.SkipPrevious, "Предыдущая серия", onPreviousEpisode, 48.dp, enabled = hasPreviousEpisode)
                        }
                        IconGlyphButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                            onClick = onTogglePlay, size = 60.dp, isPrimary = true,
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                        if (isSeries) {
                            IconGlyphButton(Icons.Default.SkipNext, "Следующая серия", onNextEpisode, 48.dp, enabled = hasNextEpisode)
                        }
                        when (kind) {
                            PlayerKind.SERIES -> ActionPill(Icons.Default.VideoLibrary, "Серии", onOpenEpisodes)
                            PlayerKind.LIVE -> {
                                ActionPill(Icons.Default.Tv, "Каналы", onOpenChannels)
                                ActionPill(Icons.Default.CalendarMonth, "Программа", onOpenProgram)
                            }
                            PlayerKind.CATCHUP -> {
                                ActionPill(Icons.Default.FiberManualRecord, "В эфир", onWatchLive)
                                ActionPill(Icons.Default.Tv, "Каналы", onOpenChannels)
                                ActionPill(Icons.Default.CalendarMonth, "Программа", onOpenProgram)
                            }
                            PlayerKind.MOVIE -> {}
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                        MenuIconButton(icon = Icons.Default.ClosedCaption, contentDescription = "Субтитры", isActive = subtitlesEnabled) { onOpenMenuTab(PlaybackMenuTab.SUBTITLES) }
                        MenuIconButton(icon = Icons.Default.Audiotrack, contentDescription = "Аудиодорожка", isActive = false) { onOpenMenuTab(PlaybackMenuTab.AUDIO) }
                        MenuIconButton(icon = Icons.Default.HighQuality, contentDescription = "Качество", isActive = false) { onOpenMenuTab(PlaybackMenuTab.QUALITY) }
                        if (!isLive) {
                            MenuIconButton(icon = Icons.Default.Speed, contentDescription = "Скорость", isActive = playbackSpeed != 1f) { onOpenMenuTab(PlaybackMenuTab.SPEED) }
                        }
                    }
                }

                // Подсказка по клавишам — показывает, что делает каждая стрелка на ТЕКУЩЕМ уровне.
                Text(
                    text = when {
                        level == 2 -> "◄ ►  выбор кнопки     ▲  к полосе     Назад  к полосе"
                        isLive -> "▲  каналы     ▼  кнопки     Назад  скрыть"
                        else -> "◄ ►  перемотка     ▼  кнопки     ▲  скрыть"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ZenithOnSurface.copy(alpha = 0.38f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun KindBadge(text: String, color: Color) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = Color.White,
        modifier = Modifier.clip(ZenithShapePill).background(color).padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Ряд 1 для живого канала: что идёт сейчас (с прогрессом передачи) и что следом. */
@Composable
private fun LiveNowNext(info: ZapBannerInfo?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (info?.currentProgramTitle == null) {
            Text("Прямой эфир", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.8f))
            if (info != null) Text("Нет данных о программе передач", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.45f))
        } else {
            Text(
                "${info.channelNumber}  ·  ${info.currentProgramTitle}",
                style = MaterialTheme.typography.bodyLarge, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (info.currentProgramProgress != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(ZenithShapePill).background(ZenithOnSurface.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(info.currentProgramProgress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
            if (info.nextProgramTitle != null) {
                Text("Далее: ${info.nextProgramTitle}", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Кнопка-«таблетка» с иконкой и подписью (Серии / Каналы / Программа / В эфир). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapePill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ZenithOnSurface.copy(alpha = 0.1f),
            focusedContainerColor = ZenithFocusContainerActive
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, ZenithOnSurface), shape = ZenithShapePill)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ZenithOnSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Реальный репорт (сравнение с TiviMate/Netflix/YouTube TV): раньше
    // здесь стоял Box с .focusProperties { canFocus = false } — play/pause
    // и след./пред. серия были НАВСЕГДА недостижимы с пульта через
    // навигацию, реальное действие шло только через скрытые глобальные
    // хоткеи (DirectionCenter/Left/Right в PlayerScreen.onKeyEvent, пока
    // фокус нигде в капсуле не стоит). Кнопка при этом визуально выглядела
    // как обычная — не было способа понять, что её нельзя "выделить".
    // Прошлая причина отключения фокуса (см. старый комментарий в
    // MIGRATION_NOTES.md/более раннюю версию этого файла) была в том, что
    // Modifier.clickable(...) даёт фокусируемость БЕЗ визуальной индикации
    // фокуса — а не в том, что фокус здесь в принципе не нужен. Решение —
    // не убирать фокус, а сделать его настоящим и видимым, как у
    // MenuIconButton ниже (тот же tv-material3 Surface).
    //
    // enabled = false (границы сериала — нет пред./след. серии) — Surface
    // остаётся фокусируемым и в disabled-состоянии (так и задумано в
    // tv-material3, см. официальную документацию Surface: "A disabled
    // surface will still be focusable"), просто тусклее и не кликабелен —
    // это лучше, чем прятать кнопку целиком: видно, что она есть, но
    // сейчас недоступна, а не путает пустым местом в ряду.
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else ZenithOnSurface.copy(alpha = 0.1f),
            focusedContainerColor = if (isPrimary) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive,
            disabledContainerColor = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else ZenithOnSurface.copy(alpha = 0.04f)
        ),
        // Белая рамка на фокусе — единственная смена фона (как у
        // MenuIconButton) была бы малозаметна на primary-заливке play/pause,
        // рамка работает одинаково на обоих вариантах кнопки.
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, ZenithOnSurface), shape = CircleShape)
        ),
        // Тот же приём, что у MenuIconButton — кнопки стоят плотно в ряду
        // капсулы, стандартный pop-эффект скейла может задевать соседнюю
        // кнопку или скруглённый край самой капсулы.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Icon(
            icon, contentDescription,
            tint = ZenithOnSurface.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.align(Alignment.Center).size(size * 0.5f)
        )
    }
}

/**
 * Маленькие квадратные иконки (субтитры/аудио/качество/скорость внизу
 * капсулы, "Подключить телефон" наверху) — tv-material3 Surface,
 * тот же принцип реального фокуса, что теперь и у IconGlyphButton выше
 * (до пятого раунда разница была принципиальной — см. комментарий там).
 * iconSize — из-за переиспользования для "Подключить телефон" в шапке
 * (четвёртый раунд, см. комментарий над PlayerController выше), которая
 * крупнее четырёх нижних иконок.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ZenithOnSurface.copy(alpha = 0.08f),
            focusedContainerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else ZenithFocusContainerActive
        ),
        // Реальный аудит пульта (тот же класс бага, что в SettingsScreen.kt/
        // SourcesScreen.kt/PlaybackMenuOverlay.kt выше): иконки стоят
        // плотно в ряд у самого края капсулы — стандартный "pop"-эффект
        // Surface при фокусе (~10%) может задевать скруглённую рамку
        // капсулы или соседнюю иконку. Отключаем, оставляем только заливку.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Icon(
            icon, contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else ZenithOnSurface,
            modifier = Modifier.padding(9.dp).size(iconSize)
        )
    }
}

@Composable
private fun ProgressBar(currentMs: Long, bufferedMs: Long, durationMs: Long, isActive: Boolean, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 4dp в покое → 10dp пока идёт перемотка (isActive выставляется
    // снаружи по частоте DirectionLeft/DirectionRight), 150ms ease-out —
    // тот же принцип, что предложен для телефона (PhonePlayerController),
    // только источник взаимодействия другой (D-pad, не касание/драг).
    val height by animateDpAsState(
        targetValue = if (isActive) 10.dp else if (emphasized) 7.dp else 4.dp,
        animationSpec = tween(durationMillis = ZenithDurationShort, easing = ZenithEasingStandard),
        label = "tvProgressBarHeight"
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(ZenithShapePill).background(ZenithOnSurface.copy(alpha = 0.15f))
    ) {
        val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        if (buffered > progress) Box(modifier = Modifier.fillMaxWidth(buffered).fillMaxHeight().clip(ZenithShapePill).background(ZenithOnSurface.copy(alpha = 0.3f)))
        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(ZenithShapePill).background(MaterialTheme.colorScheme.primary))
    }
}

/** Форматирует миллисекунды в MM:SS или HH:MM:SS */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
