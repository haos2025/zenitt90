package com.platinum.ott.presentation.phone.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.playlist.EntryClassifier
import com.platinum.ott.presentation.screens.sources.AddSourceUiState
import com.platinum.ott.presentation.screens.sources.AddSourceViewModel
import com.platinum.ott.presentation.screens.sources.splitSummary
import com.platinum.ott.ui.theme.ZenithShapeMedium

/**
 * План 07, этап 5: телефонная версия экрана итога после добавления источника
 * («Найдено: N каналов, M фильмов и сериалов») и списка групп («Разделено неверно?»).
 * Логика целиком в AddSourceViewModel — как и на TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAddSourceResultScreen(
    result: AddSourceUiState.Result,
    viewModel: AddSourceViewModel,
    onFinish: () -> Unit
) {
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showGroups) { showGroups = false }

    if (showGroups) {
        PhoneGroupOverridesView(result.sourceId, viewModel, onBack = { showGroups = false })
        return
    }

    val canQuestion = result.isM3u && result.error == null &&
        ((result.channels > 0 && result.vod > 0) || (reviewed && result.channels + result.vod > 0))

    Scaffold(topBar = { TopAppBar(title = { Text("Источник добавлен") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(ZenithDimens.paddingL),
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
        ) {
            Text(result.label, style = MaterialTheme.typography.titleMedium)
            if (result.error != null) {
                Text("⚠ Не удалось загрузить: ${result.error}", color = MaterialTheme.colorScheme.error)
                Text(
                    "Источник сохранён — повторить загрузку можно на вкладке «Источники».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Text(splitSummary(result.channels, result.vod), style = MaterialTheme.typography.titleLarge)
                val hint = when {
                    result.channels > 0 && result.vod > 0 -> "Каналы — в разделе «Каналы», фильмы и сериалы — в каталоге."
                    result.channels > 0 -> "Каналы появятся в разделе «Каналы»."
                    result.vod > 0 -> "Фильмы и сериалы появятся в каталоге."
                    else -> "В плейлисте ничего не найдено."
                }
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(48.dp), shape = ZenithShapeMedium) { Text("Готово") }
            if (canQuestion) {
                OutlinedButton(
                    onClick = { reviewed = true; viewModel.loadGroups(result.sourceId); showGroups = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ZenithShapeMedium
                ) { Text("Разделено неверно?") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneGroupOverridesView(sourceId: String, viewModel: AddSourceViewModel, onBack: () -> Unit) {
    val state by viewModel.groups.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Группы плейлиста") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.applyGroups(sourceId, onBack) },
                        enabled = !state.loading && !state.saving
                    ) { Text(if (state.saving) "Пересчёт..." else "Применить") }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.groups.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { Text("В плейлисте нет групп.") }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(ZenithDimens.paddingM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                item(key = "hint") {
                    Text(
                        "Укажите, чем является группа. Изменения применятся кнопкой «Применить».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                items(state.groups, key = { it.name }) { g ->
                    val choice = state.pending[g.name]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(ZenithDimens.paddingM)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "каналов: ${g.channels} • фильмов и сериалов: ${g.vod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(ZenithDimens.paddingS))
                            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                                FilterChip(
                                    selected = choice == EntryClassifier.OVERRIDE_LIVE,
                                    onClick = { viewModel.setGroupChoice(g.name, EntryClassifier.OVERRIDE_LIVE) },
                                    label = { Text("Это каналы") }
                                )
                                FilterChip(
                                    selected = choice == EntryClassifier.OVERRIDE_VOD,
                                    onClick = { viewModel.setGroupChoice(g.name, EntryClassifier.OVERRIDE_VOD) },
                                    label = { Text("Это фильмы и сериалы", maxLines = 1) }
                                )
                                if (choice != null) {
                                    TextButton(onClick = { viewModel.setGroupChoice(g.name, null) }) { Text("Сбросить") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
