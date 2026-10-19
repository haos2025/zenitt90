package com.platinum.ott.presentation.screens.player

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.core.subtitles.AiSource
import com.platinum.ott.core.subtitles.AutoSubtitleState
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.ui.theme.*

// Редизайн (было: QualityMenuOverlay.kt → PlaybackMenuOverlay — плоская
// боковая панель без скруглений/рамки, вкладки — просто текст с заливкой,
// строки — сплошные плашки на всю ширину). Теперь: та же четырёхвкладочная
// структура (Качество/Аудио/Субтитры/Скорость — на пульте без второй
// Menu-кнопки удобнее одна панель с разделами, чем разные горячие
// клавиши), но с закруглённой карточкой, отступом от края экрана (не
// впритык), сегментированными вкладками-пилюлями и строками с чек-иконкой
// вместо текстового "✓".
//
// Аудит пульта (по запросу после правок в PlayerScreen.kt): панель
// открывается поверх уже отрисованного PlayerController — тот никуда не
// девается из композиции (он просто визуально перекрыт), поэтому без
// явного запроса фокуса Compose продолжал бы считать "в фокусе" ту
// иконку капсулы, с которой панель открыли. С пульта в таком состоянии
// докрутить до вкладок/строк самой панели можно было не всегда — зависело
// от взаимного расположения элементов на экране, не гарантированно.
// menuFocusRequester ниже переносит фокус на активную вкладку сразу при
// появлении панели, независимо от того, что было в фокусе до открытия
// (клик по иконке капсулы или клавиша Menu — не важно). Тот же приём в
// QrScanScreen.kt (общая причина).
//
// Второй раунд редизайна (PROMPT_PLAYER_OVERLAY_REDESIGN.md): вкладка
// "Субт." раньше показывала только "Выключены" + встроенные дорожки —
// внешние субтитры по ссылке были доступны на TV ТОЛЬКО через отдельную
// кнопку/QR на самом плеере (см. PlayerController.kt/PlayerScreen.kt,
// showCompanionQr), никак не отражены в этом списке — с телефона это
// выглядело как "список дорожек плюс невзрачное поле ввода снизу", на TV
// вообще не было заметно, что такая возможность есть. Теперь "Свой файл
// по ссылке" — равноправная строка в том же списке; печатать URL пультом
// по-прежнему неудобно, так что клик по ней не открывает поле ввода на
// месте (как на телефоне), а вызывает тот же QR-поток — onRequestExternalSubtitleQr.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackMenuOverlay(
    tab: PlaybackMenuTab,
    onTabChange: (PlaybackMenuTab) -> Unit,
    variants: List<StreamVariant>,
    currentVariant: StreamVariant,
    onSelectVariant: (StreamVariant) -> Unit,
    audioTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onRequestExternalSubtitleQr: () -> Unit,
    // PROMPT_SUBTITLES.md, подзадача 8 — ручной переключатель "Субтитры"
    // (распознавание речи), отдельная сущность от subtitlesEnabled выше
    // (тот про уже существующие встроенные/внешние дорожки).
    autoSubtitleState: AutoSubtitleState,
    onToggleAutoSubtitles: () -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    // Вариант 2 (решение владельца): фокус сразу на списке (на выбранной
    // строке), вкладки НЕ фокусируются — Влево/Вправо переключают вкладки,
    // Вверх/Вниз ходят по строкам, OK выбирает и закрывает меню.
    val tabs = PlaybackMenuTab.values().toList()
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Индекс строки, на которую ставим фокус при открытии вкладки.
    val focusIdx = when (tab) {
        PlaybackMenuTab.QUALITY -> variants.indexOfFirst { it.url == currentVariant.url }
        PlaybackMenuTab.AUDIO -> audioTracks.indexOfFirst { it.isSelected }
        PlaybackMenuTab.SPEED -> speedOptions.indexOf(playbackSpeed)
        PlaybackMenuTab.SUBTITLES -> 0
    }.coerceAtLeast(0)
    // Собственный фокус панели: если в списке нет ни одной строки с listFocus (одна аудиодорожка,
    // субтитры ещё не скомпонованы), фокус всё равно остаётся ВНУТРИ панели — иначе Влево/Вправо
    // уходят в плеер и вкладки не переключаются.
    val panelFocus = remember { FocusRequester() }
    var panelHasFocus by remember { mutableStateOf(false) }
    // Отпускание OK, начавшееся ДО открытия панели (нажатие, которое открыло меню), не должно
    // «нажимать» строку: tv-Surface кликает по KeyUp. Съедаем KeyUp, пока в панели не было KeyDown.
    val okDownInPanel = remember { booleanArrayOf(false) }
    LaunchedEffect(tab) {
        try { listState.scrollToItem(focusIdx) } catch (_: Exception) {}
        // Несколько кадров: LazyColumn создаёт строки не мгновенно после смены вкладки.
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        listFocus.safeRequestFocus()
        androidx.compose.runtime.withFrameNanos { }
        if (!panelHasFocus) panelFocus.safeRequestFocus()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(
            modifier = Modifier
                .padding(ZenithDimens.paddingL)
                .width(320.dp)
                .fillMaxHeight(0.82f)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface.copy(alpha = 0.96f))
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeLarge)
                .padding(vertical = ZenithDimens.paddingL)
                .onPreviewKeyEvent { e ->
                    val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (isOk) {
                        if (e.type == KeyEventType.KeyDown) { okDownInPanel[0] = true; return@onPreviewKeyEvent false }
                        if (e.type == KeyEventType.KeyUp) {
                            if (!okDownInPanel[0]) return@onPreviewKeyEvent true // «хвост» нажатия, открывшего меню
                            okDownInPanel[0] = false
                            return@onPreviewKeyEvent false
                        }
                    }
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val i = tabs.indexOf(tab)
                    when (e.key) {
                        Key.DirectionLeft -> { onTabChange(tabs[(i - 1 + tabs.size) % tabs.size]); true }
                        Key.DirectionRight -> { onTabChange(tabs[(i + 1) % tabs.size]); true }
                        else -> false
                    }
                }
                .onFocusChanged { panelHasFocus = it.hasFocus }
                .focusRequester(panelFocus)
                .focusable()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Настройки", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(ZenithShapePill),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = ZenithFocusContainerActive),
                    // См. комментарий у MenuRow ниже — тот же фикс оверфлоу
                    // подсветки за скруглённый край узкой панели (320dp).
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) { Icon(Icons.Default.Close, "Закрыть", tint = ZenithOnSurface.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp).size(18.dp)) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            // Сегментированные вкладки-пилюли в общей "капсуле" вместо
            // отдельных прямоугольников — компактнее, читается как одна
            // группа переключателей, а не четыре разные кнопки.
            Row(
                Modifier.padding(horizontal = ZenithDimens.paddingM)
                    .focusProperties { canFocus = false }
                    .clip(ZenithShapeMedium)
                    .background(ZenithOnSurface.copy(alpha = 0.05f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MenuTabButton("Качество", tab == PlaybackMenuTab.QUALITY, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.QUALITY) }
                MenuTabButton("Аудио", tab == PlaybackMenuTab.AUDIO, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.AUDIO) }
                MenuTabButton("Субт.", tab == PlaybackMenuTab.SUBTITLES, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.SUBTITLES) }
                MenuTabButton("Скор.", tab == PlaybackMenuTab.SPEED, Modifier.weight(1f)) { onTabChange(PlaybackMenuTab.SPEED) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(horizontal = ZenithDimens.paddingSM)) {
                when (tab) {
                    PlaybackMenuTab.QUALITY -> itemsIndexed(variants, key = { _, v -> v.url }) { i, v ->
                        MenuRow(v.quality, v.source, v.url == currentVariant.url, focus = if (i == focusIdx) listFocus else null) { onSelectVariant(v) }
                    }
                    PlaybackMenuTab.AUDIO -> {
                        if (audioTracks.isEmpty()) item { EmptyHint("Только одна дорожка") }
                        itemsIndexed(audioTracks) { i, t -> MenuRow(t.label, null, t.isSelected, focus = if (i == focusIdx) listFocus else null) { onSelectAudio(t) } }
                    }
                    PlaybackMenuTab.SUBTITLES -> {
                        // PROMPT_SUBTITLES.md, подзадача 8 — первая строка,
                        // над "Выключены"/списком дорожек: включает не
                        // конкретную дорожку, а весь механизм автосубтитров
                        // (оркестратор, подзадача 6). subLabel показывает,
                        // на каком шаге приоритета источников он сейчас
                        // находится — минимальная версия "явного индикатора"
                        // из промта, полноценный вариант — подзадача 9.
                        item {
                            MenuRow(
                                label = "Автосубтитры",
                                subLabel = autoSubtitleStatusLabel(autoSubtitleState),
                                isSelected = autoSubtitleState != AutoSubtitleState.Off,
                                focus = listFocus
                            ) { onToggleAutoSubtitles() }
                        }
                        item { MenuRow("Выключены", null, !subtitlesEnabled) { onDisableSubtitles() } }
                        // Равноправный пункт списка, не поле ввода снизу —
                        // единственный способ получить субтитры-ПЕРЕВОД на
                        // другой язык (встроенные дорожки — это язык
                        // оригинала). trailingIcon вместо чек-иконки: это
                        // действие (открыть QR), а не выбор текущего
                        // состояния, отмечать как "выбрано" нечем.
                        item {
                            MenuRow(
                                label = "Свой файл по ссылке",
                                subLabel = "по QR с телефона",
                                isSelected = false,
                                trailingIcon = Icons.Default.QrCode
                            ) { onRequestExternalSubtitleQr() }
                        }
                        items(subtitleTracks) { t -> MenuRow(t.label, null, subtitlesEnabled && t.isSelected) { onSelectSubtitle(t) } }
                        if (subtitleTracks.isEmpty()) item { EmptyHint("В потоке нет встроенных субтитров") }
                    }
                    PlaybackMenuTab.SPEED -> itemsIndexed(speedOptions) { i, s ->
                        MenuRow((if (s == 1f) "Обычная" else "${s}x"), null, s == playbackSpeed, focus = if (i == focusIdx) listFocus else null) { onSelectSpeed(s) }
                    }
                }
            }
        }
    }
}

// PROMPT_SUBTITLES.md, подзадача 8 — то же самое сопоставление статуса в
// текст нужно и на TV (здесь), и на телефоне (PhonePlayerController.kt) —
// не общий файл, т.к. это единственное пересечение между двумя иначе
// полностью раздельными UI-иерархиями (TV Compose vs обычный Compose
// Material3), заводить общий модуль ради одной строчки избыточно.
private fun autoSubtitleStatusLabel(state: AutoSubtitleState): String? = when (state) {
    AutoSubtitleState.Off -> null
    AutoSubtitleState.SearchingOpenSubtitles -> "Ищем готовые субтитры…"
    is AutoSubtitleState.UsingOpenSubtitles -> "OpenSubtitles (${state.language})"
    is AutoSubtitleState.GeneratingAi -> when (state.source) {
        AiSource.CLOUD -> "Распознаём (облако)…"
        AiSource.LOCAL -> "Распознаём (локально, может отставать)…"
    }
    is AutoSubtitleState.Error -> "Ошибка: ${state.message}"
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun EmptyHint(text: String) {
    Text(text, color = ZenithOnSurface.copy(0.4f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(ZenithDimens.paddingM))
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuTabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive
        ),
        // Реальный аудит пульта (тот же класс бага, что уже чинили в
        // SettingsScreen.kt/SourcesScreen.kt): по умолчанию у tv-material3
        // Surface фокус ещё и увеличивает сам компонент (~10%, "pop") —
        // рассчитано на карточки с гуттерами вокруг (постеры), а не на
        // сегментированные вкладки-пилюли впритык друг к другу и к краю
        // узкой 320dp-панели. Отключаем масштаб, оставляем только заливку
        // фона как индикатор — тот же фикс, что и у MenuRow ниже.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun MenuRow(
    label: String,
    subLabel: String?,
    isSelected: Boolean,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    focus: FocusRequester? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
        shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = ZenithFocusContainerActive
        ),
        // Реальный аудит пульта: строка на всю ширину узкой (320dp) панели
        // почти впритык к её скруглённой рамке — стандартный "pop"-эффект
        // tv-material3 Surface при фокусе (~10% увеличение) вылезал бы за
        // эту рамку, тот же класс бага, что уже чинили в SettingsScreen.kt/
        // SourcesScreen.kt для похожих полноширинных строк списка.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else ZenithOnSurface)
                subLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.45f)) }
            }
            when {
                // Действие (например "Свой файл по ссылке" → QR), а не
                // текущий выбор — trailingIcon показывает ЧТО произойдёт,
                // не помечает строку как "выбранную".
                trailingIcon != null -> Icon(trailingIcon, null, tint = ZenithOnSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                isSelected -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
