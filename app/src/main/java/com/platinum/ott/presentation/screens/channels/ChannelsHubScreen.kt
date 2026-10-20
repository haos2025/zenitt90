package com.platinum.ott.presentation.screens.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Раздел «Каналы» на ТВ. Вкладка «Источники» убрана (21.10.2026) — источники теперь в
 * «Настройки → Источники»; остался только экран «Смотреть» с режимом «Править».
 * Имя Hub и параметр [initialTab] сохранены, чтобы не ломать маршрут `channels?tab=…&edit=…`
 * и старые ссылки (tab игнорируется).
 */
@Composable
fun ChannelsHubScreen(
    @Suppress("UNUSED_PARAMETER") initialTab: String,
    initialEdit: Boolean,
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onAddSourceClick: () -> Unit,
    onOpenSources: () -> Unit
) {
    var editMode by rememberSaveable { mutableStateOf(initialEdit) }
    ChannelsBrowseScreen(
        onBackPressed = onBackPressed,
        onPlayChannel = onPlayChannel,
        editMode = editMode,
        onEditModeChange = { editMode = it },
        onAddSourceClick = onAddSourceClick,
        onOpenSources = onOpenSources
    )
}
