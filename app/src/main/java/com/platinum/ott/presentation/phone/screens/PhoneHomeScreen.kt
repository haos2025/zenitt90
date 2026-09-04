package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.platinum.ott.core.platform.ZenithDimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.phone.components.PhoneCatalogRow
import com.platinum.ott.presentation.phone.components.PhoneHeroBanner
import com.platinum.ott.presentation.components.SkeletonCatalog
import com.platinum.ott.presentation.screens.home.HomeContentFilter
import com.platinum.ott.presentation.screens.home.HomeUiState
import com.platinum.ott.presentation.screens.home.HomeViewModel
import com.platinum.ott.presentation.screens.home.isPlaylistSourced
import com.platinum.ott.presentation.screens.home.matchesFilter

/**
 * Раньше здесь был плоский LazyVerticalGrid(GridCells.Fixed(3)) — карточки
 * от MovieCard.kt задают СВОЮ фиксированную ширину (ZenithDimens.cardWidth,
 * Platform.kt) — для Compact-экрана это 140dp. 140×3 = 420dp только под
 * карточки, без учёта отступов между ними — больше ширины почти любого
 * телефона (обычно 360-412dp), поэтому карточки визуально вылезали за
 * границы грида. LazyRow (как на TV) не делит ширину поровну — карточка
 * держит свою декларативную ширину, ряд просто скроллится горизонтально,
 * переполнение исчезает само по себе, без обрезки контента.
 *
 * PROMPT_HOME_FEED_REDESIGN.md — те же пять решений, что и на TV (см.
 * HomeScreen.kt): hero-баннер, "Продолжить просмотр", персонализация по
 * жанрам, маркер "Мой плейлист", вкладки-фильтры + переписанный триггер
 * пагинации с учётом новых элементов в начале списка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(navController: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(HomeContentFilter.ALL) }

    Scaffold(
        // Раньше здесь была ещё и лупа рядом с "Сериалы" — до того, как
        // "Поиск" появился в PhoneBottomBar.kt (PROMPT_NAVIGATION_SIDEBAR.md).
        // Теперь это дублирующий вход на тот же экран (виден на скриншоте:
        // лупа и сверху, и в нижней панели одновременно) — убрана отсюда,
        // "Поиск" снизу остаётся единственным входом. "Сериалы" — другой
        // экран (browse-всех-сериалов), не дублируется больше нигде, остаётся.
        topBar = {
            TopAppBar(
                title = { Text("ZENITH", color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = { navController.navigateToTab("series") }) { Icon(Icons.Default.Movie, "Сериалы") }
                }
            )
        },
        bottomBar = { PhoneBottomBar(navController) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                // Раньше здесь был голый CircularProgressIndicator — SkeletonCatalog
                // имитирует форму будущих рядов, тот же адаптивный размер карточек.
                is HomeUiState.Loading -> SkeletonCatalog(modifier = Modifier.fillMaxSize())
                is HomeUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        Button(onClick = { viewModel.loadCatalog() }) { Text("Повторить") }
                    }
                }
                is HomeUiState.Success -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                            modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
                        ) {
                            HomeContentFilter.values().forEach { filter ->
                                FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter }, label = { Text(filter.label) })
                            }
                        }

                        val filteredMovies = remember(state.movies, selectedFilter) {
                            state.movies.filter { it.matchesFilter(selectedFilter) }
                        }
                        val grouped = remember(filteredMovies, viewModel.genrePriority) {
                            filteredMovies.groupBy { it.genre.ifBlank { "Каталог" } }
                                .entries.sortedByDescending { viewModel.genrePriority[it.key] ?: 0 }
                        }
                        val heroMovies = state.heroMovies
                        val listState = rememberLazyListState()

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

                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM), contentPadding = PaddingValues(top = ZenithDimens.paddingS, bottom = ZenithDimens.paddingM)) {
                            if (heroMovies.isNotEmpty()) {
                                item(key = "hero") {
                                    PhoneHeroBanner(
                                        movies = heroMovies, onMovieClick = { navController.navigate("detail/$it") },
                                        resolvedPosters = viewModel.resolvedPosters,
                                        onResolvePoster = { movie, widthPx -> viewModel.resolvePosterIfNeeded(movie, widthPx) }
                                    )
                                }
                            }
                            if (continueWatching.isNotEmpty()) {
                                item(key = "continue_watching") {
                                    PhoneContinueWatchingRow(entries = continueWatching, onClick = { navController.navigate("detail/$it") })
                                }
                            }
                            grouped.forEach { (genre, movies) ->
                                item(key = genre) {
                                    PhoneCatalogRow(
                                        title = genre, movies = movies, onMovieClick = { navController.navigate("detail/$it") },
                                        resolvedPosters = viewModel.resolvedPosters,
                                        onResolvePoster = { movie, widthPx -> viewModel.resolvePosterIfNeeded(movie, widthPx) },
                                        isPlaylistRow = movies.firstOrNull()?.isPlaylistSourced == true
                                    )
                                }
                            }
                            if (state.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(Modifier.fillMaxWidth().padding(ZenithDimens.paddingM), Alignment.Center) { CircularProgressIndicator() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneContinueWatchingRow(entries: List<WatchHistoryEntity>, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Продолжить просмотр",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM),
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
        ) {
            items(entries, key = { it.contentId }) { entry ->
                MovieCard(title = entry.title, poster = entry.poster ?: "", year = 0, onClick = { onClick(entry.contentId) })
            }
        }
    }
}
