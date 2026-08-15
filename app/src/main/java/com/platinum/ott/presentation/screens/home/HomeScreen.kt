package com.platinum.ott.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.CatalogRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClick: (String) -> Unit, onSettingsClick: () -> Unit, onFavoritesClick: () -> Unit, onHistoryClick: () -> Unit, onSearchClick: () -> Unit = {}, onSeriesClick: () -> Unit = {}, modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = ZenithDimens.paddingL, bottom = ZenithDimens.paddingL)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingL)) {
            Text("ZENITH", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            val ttvColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onSearchClick, colors = ttvColors) { Text("Поиск") }
            TextButton(onClick = onSeriesClick, colors = ttvColors) { Text("Сериалы") }
            TextButton(onClick = onFavoritesClick, colors = ttvColors) { Text("Избранное") }
            TextButton(onClick = onHistoryClick, colors = ttvColors) { Text("История") }
            TextButton(onClick = onSettingsClick, colors = ttvColors) { Text("Настройки") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        when (val state = uiState) {
            is HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is HomeUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error); Button(onClick = { viewModel.loadCatalog() }) { Text("Повторить") } }
            is HomeUiState.Success -> {
                // Раньше здесь был единый LazyVerticalGrid по всем фильмам сразу —
                // CatalogRow.kt (ряды по жанрам, LazyRow с D-pad навигацией) был
                // готов, но нигде не импортировался. Группируем уже полученный
                // список по Movie.genre — без изменений в HomeViewModel/
                // GetCatalogUseCase на этом этапе. Фильмы без жанра идут в общий ряд.
                val grouped = remember(state.movies) {
                    state.movies.groupBy { it.genre.ifBlank { "Каталог" } }
                }
                val listState = rememberLazyListState()

                // Раньше подгрузки следующей страницы не было вообще — весь
                // каталог = одна страница backend (у Archive.org 20 фильмов).
                // Триггерим loadMore() когда до конца списка рядов остаётся
                // 2 или меньше — с запасом, чтобы пользователь не упирался в
                // видимый край списка прежде чем подгрузка начнётся.
                LaunchedEffect(listState, grouped.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { lastVisibleIndex ->
                            if (lastVisibleIndex != null && lastVisibleIndex >= grouped.size - 2) {
                                viewModel.loadMore()
                            }
                        }
                }

                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                    grouped.forEach { (genre, movies) ->
                        item(key = genre) {
                            CatalogRow(
                                title = genre, movies = movies, onMovieClick = onMovieClick,
                                resolvedPosters = viewModel.resolvedPosters,
                                onResolvePoster = { movie, widthPx -> viewModel.resolvePosterIfNeeded(movie, widthPx) }
                            )
                        }
                    }
                    if (state.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(Modifier.fillMaxWidth().padding(ZenithDimens.paddingL), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
