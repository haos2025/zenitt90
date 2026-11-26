package com.platinum.ott.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.ZenithShapeSmall
import kotlinx.coroutines.delay

/**
 * Фокус горизонтального ряда карточек (актёры, «Смотрите также», фильмография).
 *
 * Что исправляет:
 *  - при входе в ряд сверху или снизу фокус шёл на ближайшую по геометрии карточку (у рядов разная ширина
 *    карточек, поэтому попадал «не туда»); теперь — на ту, где стоял в прошлый раз;
 *  - после перехода на экран актёра/фильма и «Назад» фокус сбрасывался на «Смотреть»; состояние ряда
 *    (последняя карточка и прокрутка) сохраняется вместе с экраном и [restoreFocus] возвращает фокус на неё.
 */
@Stable
class RowFocusState(val listState: LazyListState, initialIndex: Int = 0) {
    var lastIndex by mutableIntStateOf(initialIndex)
    private val requesters = HashMap<Int, FocusRequester>()

    fun requesterFor(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }

    /** Куда отдать фокус при входе в ряд: на последнюю выбранную карточку, если она сейчас на экране. */
    fun enterTarget(): FocusRequester {
        val onScreen = listState.layoutInfo.visibleItemsInfo.any { it.index == lastIndex }
        return if (onScreen) requesterFor(lastIndex) else FocusRequester.Default
    }

    /** Ждёт, пока карточка появится на экране (после возврата со вложенного экрана), и ставит на неё фокус. */
    suspend fun restoreFocus(): Boolean {
        repeat(30) {
            if (runCatching { requesterFor(lastIndex).requestFocus() }.getOrDefault(false)) return true
            delay(50)
        }
        return false
    }
}

@Composable
fun rememberRowFocusState(): RowFocusState = rememberSaveable(
    saver = listSaver<RowFocusState, Int>(
        save = { listOf(it.lastIndex, it.listState.firstVisibleItemIndex, it.listState.firstVisibleItemScrollOffset) },
        restore = { RowFocusState(LazyListState(it[1], it[2]), it[0]) }
    )
) { RowFocusState(LazyListState()) }

/** Поле вокруг карточек в ряду: подсвеченная карточка увеличивается и не должна обрезаться краем ряда. */
val RowContentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)

/** Ряд — группа фокуса: вход в неё (с кнопок сверху, с соседнего ряда) идёт на запомненную карточку. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun Modifier.rowEntry(state: RowFocusState): Modifier =
    this.focusProperties { enter = { state.enterTarget() } }.focusGroup()

/**
 * Карточка ряда: постер/фото с рамкой и увеличением при фокусе, подпись снизу.
 * [caption] получает признак фокуса (подсветка текста).
 */
@Composable
fun FocusRowCard(
    width: Dp,
    imageHeight: Dp,
    state: RowFocusState,
    index: Int,
    onClick: () -> Unit,
    image: @Composable BoxScope.() -> Unit,
    caption: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, animationSpec = tween(120), label = "rowCardScale")
    Column(Modifier.width(width).zIndex(if (focused) 1f else 0f)) {
        Box(
            modifier = Modifier.width(width).height(imageHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(ZenithShapeSmall)
                .background(MaterialTheme.colorScheme.surface)
                .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, ZenithShapeSmall)
                .focusRequester(state.requesterFor(index))
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) state.lastIndex = index
                }
                .clickable(onClick = onClick),
            content = image
        )
        Spacer(Modifier.height(ZenithDimens.paddingXS))
        caption(focused)
    }
}
