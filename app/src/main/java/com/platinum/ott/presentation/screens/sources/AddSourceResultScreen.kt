package com.platinum.ott.presentation.screens.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.data.playlist.EntryClassifier
import com.platinum.ott.ui.theme.ZenithFocusContainer
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithShapeSmall

/**
 * План 07, этап 3: экран после добавления источника — итог разведения
 * («Найдено: N каналов, M фильмов и сериалов») и, если разведение могло
 * ошибиться, переход к списку групп («Разделено неверно?»).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AddSourceResultScreen(
    result: AddSourceUiState.Result,
    viewModel: AddSourceViewModel,
    onFinish: () -> Unit
) {
    var showGroups by rememberSaveable { mutableStateOf(false) }
    // Вопрос показываем, когда есть и каналы, и фильмы; после открытия списка групп он остаётся
    // (иначе, сделав одну из сторон пустой, пользователь не смог бы вернуть как было).
    var reviewed by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showGroups) { showGroups = false }

    if (showGroups) {
        GroupOverridesView(
            sourceId = result.sourceId,
            viewModel = viewModel,
            onApplied = { showGroups = false }
        )
        return
    }

    val doneRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { doneRequester.safeRequestFocus() }

    val canQuestion = result.isM3u && result.error == null &&
        ((result.channels > 0 && result.vod > 0) || (reviewed && result.channels + result.vod > 0))

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
            modifier = Modifier.align(Alignment.Center).width(520.dp)
        ) {
            Text(
                if (result.error == null) "Источник добавлен" else "Источник добавлен, но не загрузился",
                style = MaterialTheme.typography.displaySmall,
                color = ZenithOnSurface
            )
            Text(result.label, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface.copy(alpha = 0.7f))

            if (result.error != null) {
                Text("⚠ ${result.error}", color = MaterialTheme.colorScheme.error)
                Text(
                    "Источник сохранён — повторить загрузку можно на вкладке «Источники».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenithOnSurface.copy(alpha = 0.6f)
                )
            } else {
                Text(splitSummary(result.channels, result.vod), style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
                val hint = when {
                    result.channels > 0 && result.vod > 0 -> "Каналы — в разделе «Каналы», фильмы и сериалы — в каталоге."
                    result.channels > 0 -> "Каналы появятся в разделе «Каналы»."
                    result.vod > 0 -> "Фильмы и сериалы появятся в каталоге."
                    else -> "В плейлисте ничего не найдено."
                }
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.6f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                Button(
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    onClick = onFinish,
                    modifier = Modifier.focusRequester(doneRequester)
                ) { Text("Готово") }
                if (canQuestion) {
                    OutlinedButton(
                        scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
                        onClick = { reviewed = true; viewModel.loadGroups(result.sourceId); showGroups = true }
                    ) { Text("Разделено неверно?") }
                }
            }
        }
    }
}

private fun splitSummary(channels: Int, vod: Int): String {
    val parts = ArrayList<String>()
    if (channels > 0) parts.add("$channels ${plural(channels, "канал", "канала", "каналов")}")
    if (vod > 0) parts.add("$vod ${plural(vod, "фильм или сериал", "фильма и сериала", "фильмов и сериалов")}")
    return if (parts.isEmpty()) "Найдено: 0" else "Найдено: " + parts.joinToString(", ")
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}

/** Список групп источника: «Это каналы» / «Это фильмы и сериалы» / «Сбросить» на каждую; применяется разом. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupOverridesView(
    sourceId: String,
    viewModel: AddSourceViewModel,
    onApplied: () -> Unit
) {
    val state by viewModel.groups.collectAsStateWithLifecycle()
    val applyRequester = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading) applyRequester.safeRequestFocus() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
            modifier = Modifier.align(Alignment.Center).width(820.dp).fillMaxSize().padding(vertical = ZenithDimens.paddingL)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Группы плейлиста", style = MaterialTheme.typography.headlineMedium, color = ZenithOnSurface)
                    Text(
                        "Укажите, чем является группа. Изменения применятся кнопкой справа.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZenithOnSurface.copy(alpha = 0.6f)
                    )
                }
                Button(
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    onClick = { viewModel.applyGroups(sourceId, onApplied) },
                    enabled = !state.loading && !state.saving,
                    modifier = Modifier.focusRequester(applyRequester)
                ) { Text(if (state.saving) "Пересчёт..." else "Применить") }
            }

            when {
                state.loading -> Text("Загрузка...", color = ZenithOnSurface.copy(alpha = 0.6f))
                state.groups.isEmpty() -> Text("В плейлисте нет групп.", color = ZenithOnSurface.copy(alpha = 0.6f))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.groups, key = { it.name }) { g ->
                        val choice = state.pending[g.name]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(g.name, color = ZenithOnSurface, maxLines = 1)
                                Text(
                                    "каналов: ${g.channels} • фильмов и сериалов: ${g.vod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZenithOnSurface.copy(alpha = 0.5f)
                                )
                            }
                            ChoiceChip("Это каналы", choice == EntryClassifier.OVERRIDE_LIVE) {
                                viewModel.setGroupChoice(g.name, EntryClassifier.OVERRIDE_LIVE)
                            }
                            ChoiceChip("Это фильмы и сериалы", choice == EntryClassifier.OVERRIDE_VOD) {
                                viewModel.setGroupChoice(g.name, EntryClassifier.OVERRIDE_VOD)
                            }
                            ChoiceChip("Сбросить", false, enabled = choice != null) {
                                viewModel.setGroupChoice(g.name, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainer,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) ZenithOnSurface else ZenithOnSurface.copy(alpha = if (enabled) 0.7f else 0.3f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
