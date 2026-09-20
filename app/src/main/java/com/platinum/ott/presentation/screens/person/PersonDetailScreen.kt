package com.platinum.ott.presentation.screens.person

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.PersonFilmographyRow
import com.platinum.ott.ui.theme.*
import kotlinx.coroutines.launch

private val photoWidth = 220.dp
private val photoHeight = 330.dp

/**
 * Экран актёра (подзадача 4 прогона про фокус/оверлей/актёров) — открывается
 * по клику на карточку в CastRow.kt, которая раньше была осознанно
 * .focusable() без onClick ("актёр никуда не ведёт"). Данные — новый
 * TmdbRepository.getPersonProfile() (/person/{id} + /person/{id}/combined_credits),
 * живой запрос без кэша в Room — тот же принцип, что и у getRecommendations()
 * в DetailScreen.kt (один актёр на время открытого экрана, не 20-30
 * карточек ленты разом).
 *
 * Фильмография разделена на две секции (Фильмы/Сериалы по media_type) —
 * согласовано с Shadow, не единый список по дате. Клик по карточке —
 * тот же приём, что и в "Смотрите также" DetailScreen.kt: TMDB-запись
 * необязательно есть в собственном каталоге приложения, ищем по названию
 * через PersonDetailViewModel.findInCatalog(), либо переходим, либо
 * явно говорим, что не нашли.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonDetailScreen(personId: Int, onBackPressed: () -> Unit, onNavigateToMovie: (String) -> Unit = {}, viewModel: PersonDetailViewModel = hiltViewModel()) {
    LaunchedEffect(personId) { viewModel.load(personId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is PersonUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is PersonUiState.Error -> Column(Modifier.align(Alignment.Center).padding(ZenithDimens.paddingXL)) {
                Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = onBackPressed) { Text("Назад") }
            }
            is PersonUiState.Success -> {
                val profile = state.profile
                val density = LocalDensity.current
                val widthPx = with(density) { photoWidth.roundToPx() }
                val heightPx = with(density) { photoHeight.roundToPx() }
                val request = remember(profile.profilePath) {
                    TmdbImage.profileUrl(profile.profilePath, widthPx)?.let {
                        ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build()
                    }
                }
                // media_type у combined_credits — единственное надёжное поле
                // для разделения на секции (см. комментарий у TmdbPersonCreditItem).
                val movies = remember(profile.filmography) { profile.filmography.filter { it.mediaType == "movie" } }
                val series = remember(profile.filmography) { profile.filmography.filter { it.mediaType == "tv" } }
                val onCreditClick: (com.platinum.ott.domain.model.PersonCreditItem) -> Unit = { credit ->
                    scope.launch {
                        val foundId = viewModel.findInCatalog(credit)
                        if (foundId != null) onNavigateToMovie(foundId)
                        else Toast.makeText(context, "«${credit.title}» не найден в каталоге", Toast.LENGTH_SHORT).show()
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize().padding(ZenithDimens.paddingXL), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                            Box(
                                modifier = Modifier.width(photoWidth).height(photoHeight)
                                    .clip(ZenithShapeMedium).background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (request != null) {
                                    AsyncImage(
                                        model = request, contentDescription = profile.name, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = profile.name, tint = Color.Gray,
                                        modifier = Modifier.align(Alignment.Center).size(96.dp))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), modifier = Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.displaySmall, color = Color.White)
                                // Категоризированная инфа — каждое поле своей
                                // строкой, а не одной "простынёй" через " · ",
                                // как metaLine у фильма: у актёра это разнородные
                                // факты (профессия/даты/место), не одна строка
                                // метаданных одного порядка.
                                profile.department?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                                profile.birthday?.let { birthday ->
                                    val line = if (profile.deathday != null) "$birthday — ${profile.deathday}" else birthday
                                    Text("Дата рождения: $line", color = Color.White.copy(0.8f))
                                }
                                profile.placeOfBirth?.let { Text("Место рождения: $it", color = Color.White.copy(0.8f)) }
                                profile.biography?.let { bio ->
                                    // Тот же line-clamp паттерн, что у overview
                                    // фильма (подзадача 3) — 6 строк вместо 4:
                                    // биография обычно длиннее описания фильма,
                                    // и здесь под ней нет карусели постеров сразу
                                    // же, места больше.
                                    var isBioExpanded by remember(personId) { mutableStateOf(false) }
                                    var isBioExpandable by remember(personId) { mutableStateOf(false) }
                                    Text(
                                        bio, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyLarge,
                                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 6,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { if (!isBioExpanded) isBioExpandable = it.hasVisualOverflow }
                                    )
                                    if (isBioExpandable) {
                                        Surface(
                                            onClick = { isBioExpanded = !isBioExpanded },
                                            shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = Color.Transparent,
                                                focusedContainerColor = ZenithFocusContainerActive
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                        ) {
                                            Text(
                                                if (isBioExpanded) "Свернуть" else "Показать полностью",
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = ZenithDimens.paddingS, vertical = ZenithDimens.paddingXS)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (movies.isNotEmpty()) {
                        item { PersonFilmographyRow("Фильмы", movies, onItemClick = onCreditClick) }
                    }
                    if (series.isNotEmpty()) {
                        item { PersonFilmographyRow("Сериалы", series, onItemClick = onCreditClick) }
                    }
                }
            }
        }
    }
}
