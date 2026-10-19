package com.platinum.ott.presentation.screens.channels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.presentation.screens.sources.SourcesScreen
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithShapeSmall

const val CHANNELS_TAB_WATCH = "watch"
const val CHANNELS_TAB_SOURCES = "sources"

/**
 * Раздел «Каналы» на ТВ (план 07, этап 2): вкладки «Смотреть» и «Источники».
 * «Смотреть» — ChannelsBrowseScreen с режимом «Править» (вместо прежнего отдельного экрана
 * «Управление»), «Источники» — SourcesScreen. Вкладки выбираются по OK (не по фокусу): иначе
 * смена содержимого забирала бы фокус прямо во время перемещения по вкладкам.
 * После переключения фокус возвращается на выбранную вкладку.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsHubScreen(
    initialTab: String,
    initialEdit: Boolean,
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onAddSourceClick: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(if (initialTab == CHANNELS_TAB_SOURCES) CHANNELS_TAB_SOURCES else CHANNELS_TAB_WATCH) }
    var editMode by rememberSaveable { mutableStateOf(initialEdit) }
    var switched by remember { mutableStateOf(false) }
    val watchTabRequester = remember { FocusRequester() }
    val sourcesTabRequester = remember { FocusRequester() }

    LaunchedEffect(tab) {
        if (switched) {
            // Ждём кадр: вкладки нового содержимого появляются в композиции только сейчас.
            androidx.compose.runtime.withFrameNanos { }
            (if (tab == CHANNELS_TAB_SOURCES) sourcesTabRequester else watchTabRequester).safeRequestFocus()
        }
    }

    val tabs: @Composable () -> Unit = {
        Row {
            ChannelsTabPill("Смотреть", tab == CHANNELS_TAB_WATCH, watchTabRequester) {
                if (tab != CHANNELS_TAB_WATCH) { switched = true; tab = CHANNELS_TAB_WATCH }
            }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            ChannelsTabPill("Источники", tab == CHANNELS_TAB_SOURCES, sourcesTabRequester) {
                if (tab != CHANNELS_TAB_SOURCES) { switched = true; tab = CHANNELS_TAB_SOURCES; editMode = false }
            }
        }
    }

    if (tab == CHANNELS_TAB_SOURCES) {
        SourcesScreen(onBackPressed = onBackPressed, onAddSourceClick = onAddSourceClick, headerExtra = tabs)
    } else {
        ChannelsBrowseScreen(
            onBackPressed = onBackPressed,
            onPlayChannel = onPlayChannel,
            editMode = editMode,
            onEditModeChange = { editMode = it },
            tabs = tabs,
            autoFocusContent = !switched
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelsTabPill(title: String, selected: Boolean, requester: FocusRequester, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ZenithOnSurface.copy(alpha = 0.06f),
            focusedContainerColor = ZenithFocusContainerActive
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.focusRequester(requester)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else ZenithOnSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM)
        )
    }
}
