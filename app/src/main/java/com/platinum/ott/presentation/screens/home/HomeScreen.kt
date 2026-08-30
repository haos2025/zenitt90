package com.platinum.ott.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.presentation.components.CatalogRow
import com.platinum.ott.presentation.components.HeroBanner
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.components.NavSidebar
import com.platinum.ott.presentation.components.SkeletonCatalog

// PROMPT_NAVIGATION_SIDEBAR.md — прежний локальный ряд TextButton
// (Поиск/Сериалы/Избранное/История/Настройки) убран целиком, заменён
// постоянным сайдбаром слева (NavSidebar.kt, общий для всех верхнеуровневых
// TV-экранов). "Сериалы" как отдельный пункт не переехал в сайдбар — его
// закрывает вкладка-фильтр "Сериалы" прямо в ленте (решение 5 ниже),
// отдельный экран browse-всех-сериалов с Home больше не связан.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, onMovieClick: (String) -> Unit, modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(HomeContentFilter.ALL) }

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = ZenithDimens.paddingL, bottom = ZenithDimens.paddingL)) {
            Text("ZENITH", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = ZenithDimens.paddingL))
            Spacer(Modifier.height(ZenithDimens.paddingM))

            // Решение 5 — вкладки-фильтры сверху, чистый клиентский фильтр
            // уже известного типа контента, ничего не считаем и не
            // запрашиваем заново с сервера (см. Movie.matchesFilter).
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                modifier = Modifier.padding(horizontal = ZenithDimens.paddingL)
            ) {
                HomeContentFilter.values().forEach { filter ->
                    FilterChip(selectedFilter == filter, onClick = { selectedFilter = filter }) { Text(filter.label) }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            when (val state = uiState) {
                // Раньше здесь был голый CircularProgressIndicator — SkeletonCatalog
                // имитирует форму будущих рядов, тот же адаптивный размер карточек.
                is HomeUiState.Loading -> SkeletonCatalog(modifier = Modifier.fillMaxSize())
                is HomeUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error); Button(onClick = { viewModel.loadCatalog() }) { Text("Повторить") } }
                is HomeUiState.Success -> {
                    val filteredMovies = remember(state.movies, selectedFilter) {
                        state.movies.filter { it.matchesFilter(selectedFilter) }
                    }
                    // Решение 3 — персонализация: жанровые ряды с более
                    // высоким счётчиком (viewModel.genrePriority, считается
                    // в HomeViewModel один раз на loadCatalog()) идут выше.
                    // sortedByDescending стабилен — ряды без сигнала
                    // (приоритет 0) сохраняют исходный порядок между собой.
                    val grouped = remember(filteredMovies, viewModel.genrePriority) {
                        filteredMovies.groupBy { it.genre.ifBlank { "Каталог" } }
                            .entries.sortedByDescending { viewModel.genrePriority[it.key] ?: 0 }
                    }
                    val heroMovies = state.heroMovies
                    val listState = rememberLazyListState()

                    // Решение 1/2 меняют структуру списка — hero-баннер и
                    // ряд "Продолжить просмотр" (если есть) идут ПЕРЕД
                    // жанровыми рядами как отдельные LazyColumn-элементы.
                    // Переписано заново, не патч поверх старого смещения:
                    // триггер подгрузки считает реальное количество
                    // элементов (headerOffset + жанровые ряды), а не только
                    // grouped.size, как раньше.
                    val headerOffset = (if (heroMovies.isNotEmpty()) 1 else 0) + (if (continueWatching.isNotEmpty()) 1 else 0)
                    val totalItems = headerOffset + grouped.size

                    LaunchedEffect(listState, totalItems) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                            .collect { lastVisibleIndex ->
                                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 2) {
                                    viewModel.loadMore()
                                }
                            }
                    }

                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                        if (heroMovies.isNotEmpty()) {
                            item(key = "hero") {
                                HeroBanner(
                                    movies = heroMovies, onMovieClick = onMovieClick,
                                    resolvedPosters = viewModel.resolvedPosters,
                                    onResolvePoster = { movie, widthPx -> viewModel.resolvePosterIfNeeded(movie, widthPx) }
                                )
                            }
                        }
                        if (continueWatching.isNotEmpty()) {
                            item(key = "continue_watching") {
                                ContinueWatchingRow(entries = continueWatching, onClick = onMovieClick)
                            }
                        }
                        grouped.forEach { (genre, movies) ->
                            item(key = genre) {
                                CatalogRow(
                                    title = genre, movies = movies, onMovieClick = onMovieClick,
                                    resolvedPosters = viewModel.resolvedPosters,
                                    onResolvePoster = { movie, widthPx -> viewModel.resolvePosterIfNeeded(movie, widthPx) },
                                    // Решение 4 — "Мой плейлист" визуально
                                    // отличается. Ряды формируются группировкой
                                    // по жанру, поэтому смешение источников
                                    // внутри одного ряда маловероятно (у
                                    // плейлист-контента обычно и жанра-то нет);
                                    // первый фильм ряда как признак — тот же
                                    // практичный компромисс, что и остальные
                                    // prefix-эвристики в проекте.
                                    isPlaylistRow = movies.firstOrNull()?.isPlaylistSourced == true
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
}

// Решение 2 — переиспользует WatchHistoryUseCase.getRecentDeduped()
// (см. HomeViewModel.continueWatching), рендерит записи истории тем же
// MovieCard, что и обычные ряды каталога — WatchHistoryEntity уже хранит
// готовый poster/title, отдельного TMDB-резолва здесь не нужно.
@Composable
private fun ContinueWatchingRow(entries: List<WatchHistoryEntity>, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Продолжить просмотр",
            style = MaterialTheme.typography.titleLarge,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(start = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingSM)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ZenithDimens.tvOverscanPadding),
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
        ) {
            items(entries, key = { it.contentId }) { entry ->
                MovieCard(title = entry.title, poster = entry.poster ?: "", year = 0, onClick = { onClick(entry.contentId) })
            }
        }
    }
}
