package com.platinum.ott.presentation.screens.search

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithOnSurface
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.platinum.ott.core.platform.safeRequestFocus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.platform.ZenithDimens
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.components.NavSidebar
import com.platinum.ott.presentation.screens.nearby.PhoneInputOverlay
import com.platinum.ott.sync.local.LocalProto
import com.platinum.ott.ui.theme.ZenithShapeSmall

// Раньше поиск существовал только снаружи приложения (системный поиск TV
// через MovieSearchProvider) — внутри самого приложения зайти в поиск
// текстом было вообще нельзя, incoming initialQuery — для случая, когда
// пользователь ввёл запрос в системном поиске TV и нажал "Найти" целиком
// (ACTION_SEARCH), а не выбрал готовую подсказку.
//
// PROMPT_NAVIGATION_SIDEBAR.md — добавлен постоянный сайдбар (navController).
// Кнопка "← Назад" внутри самого поля поиска оставлена как есть — она
// реально используется (в отличие от onBackPressed в FavoritesScreen.kt/
// HistoryScreen.kt), сайдбар её не заменяет, а дополняет.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavHostController, onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, initialQuery: String = "", viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery) }

    // «Ввод с телефона» (замена QR): поисковый запрос набирается на связанном телефоне — так быстрее, чем пультом.
    var showPhoneInput by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showPhoneInput) { showPhoneInput = false }

    // Голосовой ввод внутри самого экрана поиска (PROMPT_VOICE_SEARCH.md) —
    // отдельный канал от системного ACTION_SEARCH/searchable.xml (см.
    // комментарий в AndroidManifest.xml): тот уже работает через голос
    // лаунчера/Ассистента и результат приходит через initialQuery выше,
    // этот код его не заменяет и с ним не пересекается. Кнопка — чтобы не
    // выходить в лаунчер и не зависеть от того, есть ли у конкретного
    // пульта/бокса голосовой ввод вообще.
    val context = LocalContext.current
    // Не на всех Android TV есть голосовой ввод (бюджетные приставки без
    // Google-сервисов/Ассистента) — проверяем ДО показа кнопки, не после
    // нажатия, тот же принцип, что уже применён к недостижимой панели
    // плеера без кнопки Menu (см. NEXT_STEPS.md): скрыть нерабочий
    // элемент, а не дать ему упасть при нажатии.
    val voiceRecognitionAvailable = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val voiceRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            // Пустой список результатов формально не RESULT_CANCELED, но по
            // смыслу то же самое — распознать ничего не удалось, не глотаем
            // молча (см. ограничения в PROMPT_VOICE_SEARCH.md).
            if (recognized.isNullOrBlank()) voiceError = "Не удалось распознать речь"
            else {
                voiceError = null
                // Тот же метод, что уже вызывается из компаньон-QR потока
                // выше — отдельного пути обновления состояния не заводим.
                viewModel.onQueryChange(recognized)
            }
        } else {
            voiceError = "Голосовой ввод отменён"
        }
    }

    // rememberLauncherForActivityResult(RequestPermission()) — тот же
    // Compose-паттерн runtime-разрешения, что уже применён для камеры в
    // PhoneQrScanScreen.kt (там разрешение CAMERA запрашивает сама
    // Activity сканера из zxing-android-embedded; здесь такого готового
    // экрана нет, поэтому разрешение RECORD_AUDIO запрашивается явно, тем
    // же способом).
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Тот же текст, что уже используется как placeholder
                // текстового поля ниже по файлу.
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Название фильма или сериала…")
            }
            voiceRecognizerLauncher.launch(recognizerIntent)
        } else {
            voiceError = "Нужен доступ к микрофону"
        }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Поле не принимает ввод само по себе: клавиатура открывается только
                // по нажатию OK на поле (раньше всплывала сама при получении фокуса).
                SearchField(query = query, onChange = viewModel::onQueryChange, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(ZenithDimens.paddingM))
                OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { showPhoneInput = true }) { Text("С телефона") }
                // Кнопки нет вообще, если на устройстве нет голосового сервиса —
                // не показываем нерабочий элемент (см. комментарий у
                // voiceRecognitionAvailable выше).
                if (voiceRecognitionAvailable) {
                    Spacer(Modifier.width(ZenithDimens.paddingM))
                    OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = {
                        voiceError = null
                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Голосовой ввод")
                        Spacer(Modifier.width(ZenithDimens.paddingSM))
                        Text("Голос")
                    }
                }
            }
            // По образцу SearchUiState.Error ниже по файлу — короткое сообщение,
            // не отдельный новый паттерн (снэкбар и т.п.).
            voiceError?.let {
                Spacer(Modifier.height(ZenithDimens.paddingSM))
                Text("⚠ $it", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    // Пока запрос не введён — вместо статичной подсказки
                    // показываем последние уникальные запросы (если они есть),
                    // подсказка остаётся только для пустой истории.
                    if (recentSearches.isEmpty()) {
                        Text("Начните вводить название (минимум 2 символа)", color = ZenithOnSurfaceVariant)
                    } else {
                        LazyColumn {
                            lazyColumnItems(recentSearches, key = { it }) { entry ->
                                RecentSearchRow(
                                    text = entry,
                                    onClick = { viewModel.onQueryChange(entry) },
                                    onRemove = { viewModel.removeRecentSearch(entry) }
                                )
                            }
                            item(key = "__clear_history__") {
                                RecentSearchClearRow(onClick = { viewModel.clearRecentSearches() })
                            }
                        }
                    }
                }
                is SearchUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), Alignment.TopCenter) { CircularProgressIndicator() }
                is SearchUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        Text("Ничего не нашлось по запросу «$query»", color = ZenithOnSurfaceVariant)
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                            items(state.results, key = { it.id }) { movie -> MovieCard(movie = movie, onClick = { onMovieClick(movie.id) }) }
                        }
                    }
                }
            }
        }
    }

    if (showPhoneInput) {
        PhoneInputOverlay(
            mode = LocalProto.INPUT_SEARCH,
            prompt = "Введите на телефоне поисковый запрос",
            onText = { text -> viewModel.onQueryChange(text); showPhoneInput = false },
            onDismiss = { showPhoneInput = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Поле поиска: в покое — обычная кликабельная плашка (фокус на ней НЕ вызывает
// клавиатуру), по OK переходит в режим ввода и только тогда открывает IME.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var isEditing by remember { mutableStateOf(false) }
    var gotFocus by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    val inputRequester = remember { FocusRequester() }
    val idleRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // После выхода из ввода возвращаем фокус на плашку, чтобы он не «убегал».
    LaunchedEffect(isEditing) { if (!isEditing && wasEditing) idleRequester.safeRequestFocus() }

    if (!isEditing) {
        Surface(
 scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = { gotFocus = false; wasEditing = true; isEditing = true },
            shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = ZenithOnSurface.copy(0.08f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = modifier.focusRequester(idleRequester)
        ) {
            Text(
                if (query.isEmpty()) "Название фильма или сериала…" else query,
                style = TextStyle(if (query.isEmpty()) ZenithOnSurface.copy(0.4f) else ZenithOnSurface, 20.sp),
                maxLines = 1,
                modifier = Modifier.padding(ZenithDimens.paddingM, ZenithDimens.paddingSM)
            )
        }
    } else {
        LaunchedEffect(Unit) { inputRequester.safeRequestFocus(); keyboard?.show() }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(ZenithOnSurface, 20.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { isEditing = false; keyboard?.hide() }, onDone = { isEditing = false; keyboard?.hide() }),
            modifier = modifier
                .focusRequester(inputRequester)
                .background(ZenithOnSurface.copy(0.08f), ZenithShapeSmall)
                .padding(ZenithDimens.paddingM, ZenithDimens.paddingSM)
                .onFocusChanged {
                    if (it.isFocused) gotFocus = true
                    else if (gotFocus) { isEditing = false; keyboard?.hide() }
                },
            decorationBox = { inner -> inner() }
        )
    }
}

// Та же идея, что и CycleSetting в SettingsScreen.kt — фокусируемая
// область на всю строку, а не только на узкой кнопке, чтобы D-pad
// уверенно попадал. Крестик удаления — отдельный focus-target внутри
// той же строки (второй Surface), а не часть основного onClick.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentSearchRow(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
 scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text(text, color = ZenithOnSurface, modifier = Modifier.padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp))
        }
        Spacer(Modifier.width(ZenithDimens.paddingSM))
        Surface(
 scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            onClick = onRemove,
            shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            )
        ) {
            Text("✕", color = ZenithOnSurfaceVariant, modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = 14.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentSearchClearRow(onClick: () -> Unit) {
    Surface(
 scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            "Очистить историю запросов",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp)
        )
    }
}
