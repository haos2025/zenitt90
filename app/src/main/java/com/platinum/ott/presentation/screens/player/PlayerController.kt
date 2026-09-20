package com.platinum.ott.presentation.screens.player

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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerController(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    title: String = "",
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    onConnectPhone: () -> Unit = {},
    // Растёт ли сейчас прогресс-бар — управляется снаружи (PlayerScreen),
    // это единственный источник правды о том, держит ли пользователь
    // сейчас DirectionLeft/DirectionRight.
    isSeekActive: Boolean = false,
    // Состояние для подсветки активных иконок справа — то же самое, что
    // уже частично было на телефоне (subtitlesEnabled), плюс скорость.
    // Для аудио/качества устойчивого понятия "активно" нет (нет
    // единого дефолта, с которым сравнивать), поэтому эти две иконки
    // всегда нейтральные — кликабельны, просто без подсветки состояния.
    subtitlesEnabled: Boolean = false,
    playbackSpeed: Float = 1f,
    onOpenMenuTab: (PlaybackMenuTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSeries = hasNextEpisode || hasPreviousEpisode

    // Motion.kt (PROMPT_DESIGN_SYSTEM.md, подзадача 2): длительность та
    // же, что была неявным дефолтом Compose (300ms), но easing теперь
    // явно ZenithEasingStandard, а не дефолтный FastOutSlowInEasing —
    // сознательное решение свести все переходы проекта к одной кривой,
    // а не оставить два разных типа easing на разные случаи.
    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        exit    = fadeOut(animationSpec = tween(ZenithDurationMedium, easing = ZenithEasingStandard)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            if (title.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.22f).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .padding(horizontal = ZenithDimens.paddingXXL, vertical = ZenithDimens.paddingXL),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    MenuIconButton(icon = Icons.Default.PhoneAndroid, contentDescription = "Подключить телефон", isActive = false, iconSize = 28.dp, onClick = onConnectPhone)
                }
            }

            // Капсула — не на весь экран, у неё собственная плашка с рамкой
            // и скруглением, а не растянутый на всю ширину градиент.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ZenithDimens.paddingXXL)
                    .widthIn(max = 860.dp)
                    .fillMaxWidth(0.8f)
                    .clip(ZenithShapeLarge)
                    .background(ZenithSurface.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), ZenithShapeLarge)
                    .padding(horizontal = ZenithDimens.paddingXL, vertical = ZenithDimens.paddingM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
            ) {
                // Время слито в один ряд с баром (было: бар отдельной
                // строкой, время под ним отдельной строкой) — экономит
                // высоту капсулы на целую строку.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatTime(currentPositionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    ProgressBar(currentMs = currentPositionMs, durationMs = durationMs, isActive = isSeekActive, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    Text(text = formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                }

                // Один ряд: слева — транспорт (play/pause, либо
                // след./пред. эпизод по бокам для сериала), справа —
                // четыре маленькие квадратные иконки настроек
                // воспроизведения.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSeries) {
                            IconGlyphButton(
                                icon = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущая серия",
                                onClick = onPreviousEpisode,
                                enabled = hasPreviousEpisode,
                                size = 52.dp
                            )
                            Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                        }
                        IconGlyphButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                            onClick = onTogglePlay,
                            isPrimary = true,
                            size = 68.dp
                        )
                        if (isSeries) {
                            Spacer(modifier = Modifier.width(ZenithDimens.paddingL))
                            IconGlyphButton(
                                icon = Icons.Default.SkipNext,
                                contentDescription = "Следующая серия",
                                onClick = onNextEpisode,
                                enabled = hasNextEpisode,
                                size = 52.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                        MenuIconButton(icon = Icons.Default.ClosedCaption, contentDescription = "Субтитры", isActive = subtitlesEnabled) { onOpenMenuTab(PlaybackMenuTab.SUBTITLES) }
                        MenuIconButton(icon = Icons.Default.Audiotrack, contentDescription = "Аудиодорожка", isActive = false) { onOpenMenuTab(PlaybackMenuTab.AUDIO) }
                        MenuIconButton(icon = Icons.Default.HighQuality, contentDescription = "Качество", isActive = false) { onOpenMenuTab(PlaybackMenuTab.QUALITY) }
                        MenuIconButton(icon = Icons.Default.Speed, contentDescription = "Скорость", isActive = playbackSpeed != 1f) { onOpenMenuTab(PlaybackMenuTab.SPEED) }
                    }
                }
            }
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
    enabled: Boolean = true
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
        modifier = Modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
            focusedContainerColor = if (isPrimary) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive,
            disabledContainerColor = if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.04f)
        ),
        // Белая рамка на фокусе — единственная смена фона (как у
        // MenuIconButton) была бы малозаметна на primary-заливке play/pause,
        // рамка работает одинаково на обоих вариантах кнопки.
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape)
        ),
        // Тот же приём, что у MenuIconButton — кнопки стоят плотно в ряду
        // капсулы, стандартный pop-эффект скейла может задевать соседнюю
        // кнопку или скруглённый край самой капсулы.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Icon(
            icon, contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
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
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
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
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.padding(9.dp).size(iconSize)
        )
    }
}

@Composable
private fun ProgressBar(currentMs: Long, durationMs: Long, isActive: Boolean, modifier: Modifier = Modifier) {
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 4dp в покое → 10dp пока идёт перемотка (isActive выставляется
    // снаружи по частоте DirectionLeft/DirectionRight), 150ms ease-out —
    // тот же принцип, что предложен для телефона (PhonePlayerController),
    // только источник взаимодействия другой (D-pad, не касание/драг).
    val height by animateDpAsState(
        targetValue = if (isActive) 10.dp else 4.dp,
        animationSpec = tween(durationMillis = ZenithDurationShort, easing = ZenithEasingStandard),
        label = "tvProgressBarHeight"
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(ZenithShapePill).background(Color.White.copy(alpha = 0.15f))
    ) {
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
