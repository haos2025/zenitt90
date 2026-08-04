package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.phone.components.PhoneCatalogRow
import com.platinum.ott.presentation.screens.home.HomeUiState
import com.platinum.ott.presentation.screens.home.HomeViewModel

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
 * Заодно этот экран никогда не получал ни группировку по жанрам, ни
 * дозагрузку следующих страниц — HomeScreen.kt (TV) получил оба фикса
 * раньше в этой же сессии, PhoneHomeScreen.kt тогда пропустили.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(navController: NavHostController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        // Раньше на телефоне вообще не было верхней панели — единственная
        // ссылка на поиск существовала снаружи приложения (системный поиск
        // TV). Значок лупы здесь ведёт на PhoneSearchScreen.kt.
        topBar = {
            TopAppBar(
                title = { Text("ZENITH", color = Color(0xFF6C63FF)) },
                actions = { IconButton(onClick = { navController.navigate("search") }) { Icon(Icons.Default.Search, "Поиск") } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
                NavigationBarItem(selected = false, onClick = { navController.navigate("favorites") }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Избранное") })
                NavigationBarItem(selected = false, onClick = { navController.navigate("history") }, icon = { Icon(Icons.Default.History, null) }, label = { Text("История") })
                NavigationBarItem(selected = false, onClick = { navController.navigate("settings") }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF101010))) {
            when (val state = uiState) {
                is HomeUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is HomeUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadCatalog() }) { Text("Повторить") }
                    }
                }
                is HomeUiState.Success -> {
                    val grouped = remember(state.movies) {
                        state.movies.groupBy { it.genre.ifBlank { "Каталог" } }
                    }
                    val listState = rememberLazyListState()

                    LaunchedEffect(listState, grouped.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                            .collect { lastVisibleIndex ->
                                if (lastVisibleIndex != null && lastVisibleIndex >= grouped.size - 2) {
                                    viewModel.loadMore()
                                }
                            }
                    }

                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)) {
                        grouped.forEach { (genre, movies) ->
                            item(key = genre) {
                                PhoneCatalogRow(title = genre, movies = movies, onMovieClick = { navController.navigate("detail/$it") })
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}
