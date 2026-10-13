package com.platinum.ott.presentation.screens.detail

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.presentation.components.CastRow
import com.platinum.ott.presentation.components.RecommendationsRow
import com.platinum.ott.presentation.screens.favorites.MoveToFolderDialog
import com.platinum.ott.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DetailScreen(movieId: String, onPlayClick: () -> Unit, onBackPressed: () -> Unit, onNavigateToSeries: (String) -> Unit = {}, onNavigateToMovie: (String) -> Unit = {}, onNavigateToPerson: (Int) -> Unit = {}, viewModel: DetailViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Пикер папки при добавлении в избранное (PROMPT_FAVORITES_REDESIGN.md, п.2) —
    // показывается только на пути "не в избранном" -> "в избранном", снятие
    // с избранного происходит сразу, без диалога. remember(movieId) — чтобы
    // не унаследовать открытый диалог при переходе на другой фильм.
    var showAddFavoriteDialog by remember(movieId) { mutableStateOf(false) }
    // Подзадача 2 PROMPT'а про фокус/оверлей/актёров (реальный репорт:
    // "фокус на детальной карточке всегда хочет показывать кнопки актёров
    // и похожие фильмы"). Причина — нигде в TV-части проекта не было ни
    // одного явного запроса начального фокуса (проверено по всем
    // экранам), а карусели актёров/рекомендаций дописываются в
    // LazyColumn АСИНХРОННО (TMDB-метаданные приходят позже первого
    // кадра) — без явного "застолблённого" фокуса поведение Compose на
    // TV при таком дозаполнении списка непредсказуемо. Ключ movieId, а
    // не state — эффект должен сработать ровно один раз при первом
    // попадании в Success для этого фильма, а не при каждом обновлении
    // metadata (иначе фокус будет насильно дёргаться обратно на "Смотреть"
    // и после того, как пользователь сам уже ушёл дальше по экрану).
    val playButtonFocusRequester = remember(movieId) { FocusRequester() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            // Эпизод сериала — сразу уводим на общий экран сериала, не
            // рисуя здесь вообще ничего (см. DetailViewModel.load()).
            is DetailUiState.RedirectToSeries -> LaunchedEffect(state.seriesId) { onNavigateToSeries(state.seriesId) }
            is DetailUiState.Error -> Column(Modifier.align(Alignment.Center).padding(ZenithDimens.paddingXL)) { Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error) }
            is DetailUiState.Success -> {
                // Один раз на фильм (см. комментарий у объявления
                // playButtonFocusRequester выше) — переводим фокус на
                // "Смотреть" сразу, не дожидаясь, пока пользователь
                // сам его найдёт, и не давая случайно подгрузившимся
                // ниже каруселям перетянуть фокус на себя по умолчанию.
                // ФИКС (аудит): safeRequestFocus() вместо голого requestFocus() —
                // см. FocusUtils.kt про редкую IllegalStateException здесь.
                LaunchedEffect(movieId) { playButtonFocusRequester.safeRequestFocus() }
                // Раньше тут не было ни одной картинки — ни постера, ни backdrop'а,
                // хотя TMDB-метаданные (state.metadata) уже приходили с backdropPath.
                // Если TMDB backdrop недоступен — падаем на постер из бэкенда/плейлиста,
                // чтобы фон не был совсем пустым.
                // hasRealBackdrop: настоящий TMDB backdrop — широкий кадр, ему Crop
                // на весь экран идёт естественно. А movie.poster (для M3U/Xtream —
                // почти всегда просто логотип канала, TMDB такие каналы не находит)
                // на весь экран через Crop растягивался и обрезался до нечитаемости —
                // видно на присланных скриншотах.
                val context = LocalContext.current
                val density = LocalDensity.current
                val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
                val hasRealBackdrop = !state.metadata?.backdropPath.isNullOrBlank()
                // Раньше при отсутствии TMDB-backdrop'а сразу падали на
                // movie.poster — а это для Xtream/M3U почти всегда streamIcon,
                // который панель провайдера отдаёт как скриншот кадра потока,
                // не постер. TMDB-метаданные (state.metadata) при этом уже
                // содержат posterPath в большинстве случаев, даже когда
                // backdropPath пуст (у части фильмов в TMDB нет backdrop'а),
                // просто posterUrl() нигде не вызывался. Добавлена
                // промежуточная ступень: настоящий постер TMDB (порт­ретный,
                // Fit) — предпочтительнее сырого скриншота от провайдера, к
                // нему падаем только если TMDB вообще не нашёл фильм.
                val tmdbPosterUrl = TmdbImage.posterUrl(state.metadata?.posterPath, (screenWidthPx * 0.4f).toInt())
                val backdropUrl = TmdbImage.backdropUrl(state.metadata?.backdropPath, screenWidthPx)
                    ?: tmdbPosterUrl
                    ?: state.movie.poster.ifBlank { null }
                val backdropRequest = remember(backdropUrl, screenWidthPx) {
                    backdropUrl?.let { ImageRequest.Builder(context).data(it).size(screenWidthPx, screenWidthPx / 2).crossfade(true).build() }
                }
                if (backdropRequest != null) {
                    AsyncImage(
                        model = backdropRequest,
                        contentDescription = null,
                        contentScale = if (hasRealBackdrop) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.background),
                        error = ColorPainter(MaterialTheme.colorScheme.background)
                    )
                    // Скрим слева направо: TV-контролы фокусируются слева, там нужен
                    // максимальный контраст текста с фоном.
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background.copy(alpha = 0.75f), Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background), startY = 300f)))
                }
                // Раньше это был обычный Column + verticalScroll — при
                // коротком описании всё умещалось на экране, и вверх/вниз
                // "работало" просто потому, что скроллить было некуда.
                // Реальный репорт с TV: при ДЛИННОМ описании, когда контент
                // реально не помещается и требует прокрутки — вверх/вниз
                // переставало двигать фокус вообще. Причина: обычный
                // Modifier.verticalScroll() (не Lazy) не имеет НАТИВНОЙ
                // интеграции с D-pad-фокусом для докрутки к ещё невидимому
                // элементу — BringIntoViewRequester (моя предыдущая попытка
                // чинить это) отрабатывает уже ПОСЛЕ того, как фокус
                // переместился, а сама стандартная направленная
                // фокус-навигация Compose не всегда способна вообще
                // "дотянуться" до элемента, полностью прокрученного за
                // пределы видимой области.
                //
                // Первая попытка чинить это была через TvLazyColumn
                // (androidx.tv.foundation) — сборка упала: "Unresolved
                // reference 'TvLazyColumn'". Оказалось, TvLazyColumn/TvLazyRow
                // были ПОЛНОСТЬЮ удалены из tv-foundation ещё до стабильного
                // релиза 1.0.0 (который как раз и стоит в проекте) —
                // официальная миграция Google: использовать обычный
                // LazyColumn из стандартного Compose Foundation, в него
                // перенесли ту самую логику докрутки под D-pad-фокус, ради
                // которой раньше существовал отдельный TV-вариант. Обычный
                // LazyColumn ниже — это и есть официально рекомендованная
                // замена, не временный компромисс.
                // ПЕРЕДЕЛАНО (репорт с ТВ: «постер наполовину», «залипает на Показать
                // полностью», «кнопки прыгают при переходе на В избранное»). Причина — прокрутка
                // списка конкурировала сама с собой: минимальная докрутка Compose к
                // сфокусированной кнопке + моя animateScrollToItem(0) на каждое изменение
                // фокуса. Теперь экран — две «страницы» по высоте экрана: 0 = верхний блок
                // (постер, название, кнопки), 1 = актёры и «Смотрите также». Прокрутка
                // пользователем выключена; страница переключается ровно одной анимацией по
                // тому, в какой странице находится фокус. Вход в верхний блок всегда ставит
                // фокус на «Смотреть» (а не на «Показать полностью»).
                val listState = rememberLazyListState()
                var page by remember(movieId) { mutableIntStateOf(0) }
                LaunchedEffect(page) { listState.animateScrollToItem(page) }
                var showOverviewDialog by remember(movieId) { mutableStateOf(false) }
                val screenHeightDp = LocalConfiguration.current.screenHeightDp
                val posterHeight = (screenHeightDp * 0.5f).coerceIn(200f, 330f).dp
                val posterWidth = posterHeight * 2f / 3f
                val hasCast = state.metadata?.cast?.isNotEmpty() == true
                val hasMore = hasCast || state.recommendations.isNotEmpty()
                LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                    item(key = "hero") {
                        Column(
                            modifier = Modifier.fillParentMaxHeight().fillMaxWidth().padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingL),
                            verticalArrangement = Arrangement.Center
                        ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL),
                            modifier = Modifier
                                .onFocusChanged { if (it.hasFocus) page = 0 }
                                .focusProperties { enter = { playButtonFocusRequester } }
                                .focusGroup()
                        ) {
                            if (tmdbPosterUrl != null) {
                                AsyncImage(
                                    model = tmdbPosterUrl,
                                    contentDescription = state.movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(posterWidth)
                                        .height(posterHeight)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(ZenithSurface)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
                            ) {
                                Text(
                                    state.movie.title,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ZenithOnSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                state.metadata?.let { meta ->
                                    // Год / длительность — серые таблетки, рейтинг — жёлтая.
                                    // Пустые части пропускаем, чтобы не рисовать пустых таблеток.
                                    val pills = listOfNotNull(
                                        state.movie.year.takeIf { it > 0 }?.toString(),
                                        state.movie.duration.takeIf { it.isNotBlank() }
                                    )
                                    if (pills.isNotEmpty() || meta.voteAverage != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                                            meta.voteAverage?.let { DetailPill("★ $it", ZenithWarning, ZenithWarning.copy(alpha = 0.16f)) }
                                            pills.forEach { DetailPill(it, ZenithOnSurface, ZenithSurfaceVariant) }
                                        }
                                    }
                                    val genres = meta.genres.orEmpty().split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }.take(4)
                                    if (genres.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                                            genres.forEach { DetailPill(it, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) }
                                        }
                                    }
                                    meta.overview?.let { overview ->
                                        // 4 строки + «Показать полностью» — полный текст открывается в
                                        // модальном окне (раскрытие прямо в экране сдвигало кнопки).
                                        var isOverviewExpandable by remember(movieId) { mutableStateOf(false) }
                                        Text(
                                            overview,
                                            color = ZenithOnSurface.copy(0.8f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            onTextLayout = { isOverviewExpandable = it.hasVisualOverflow }
                                        )
                                        if (isOverviewExpandable) {
                                            Surface(
                                                onClick = { showOverviewDialog = true },
                                                shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    focusedContainerColor = ZenithFocusContainerActive
                                                ),
                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                            ) {
                                                Text(
                                                    "Показать полностью",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = ZenithDimens.paddingS, vertical = ZenithDimens.paddingXS)
                                                )
                                            }
                                        }
                                    }
                                }
                                // Добавление показывает выбор папки только если папки уже
                                // есть; снятие — сразу (см. DetailViewModel.addFavorite/
                                // removeFavorite). Системный Back закрывает экран, отдельной
                                // кнопки «Назад» нет.
                                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                                    Button(onClick = onPlayClick, modifier = Modifier.focusRequester(playButtonFocusRequester)) { Text(if (state.watchProgress != null) "Продолжить ${(state.watchProgress * 100).toInt()}%" else "Смотреть") }
                                    OutlinedButton(onClick = {
                                        when {
                                            state.isFavorite -> viewModel.removeFavorite(movieId)
                                            folders.isEmpty() -> viewModel.addFavorite(movieId, state.movie.title, state.movie.poster, null)
                                            else -> showAddFavoriteDialog = true
                                        }
                                    }) {
                                        Text(if (state.isFavorite) "♥ В избранном" else "♡ В избранное")
                                    }
                                }
                            }
                        }
                        if (hasMore) {
                            Spacer(Modifier.height(ZenithDimens.paddingM))
                            Text("▼  Актёры и похожие фильмы", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                        }
                    }
                    // Страница 1: актёры (если TMDB вернул credits) и «Смотрите также»
                    // (только для контента, найденного в TMDB).
                    if (hasMore) {
                        item(key = "more") {
                            Column(
                                modifier = Modifier
                                    .fillParentMaxHeight().fillMaxWidth()
                                    .padding(horizontal = ZenithDimens.paddingL, vertical = ZenithDimens.paddingL)
                                    .onFocusChanged { if (it.hasFocus) page = 1 },
                                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)
                            ) {
                                if (hasCast) CastRow(state.metadata!!.cast, onMemberClick = { member -> onNavigateToPerson(member.id) })
                                if (state.recommendations.isNotEmpty()) {
                                    // Нажатие: ищем совпадение в каталоге (DetailViewModel.findInCatalog()),
                                    // переходим либо явно сообщаем, что не нашли.
                                    RecommendationsRow(state.recommendations, onItemClick = { rec ->
                                        scope.launch {
                                            val foundId = viewModel.findInCatalog(rec)
                                            if (foundId != null) onNavigateToMovie(foundId)
                                            else Toast.makeText(context, "«${rec.title}» не найден в каталоге", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
                if (showOverviewDialog) {
                    OverviewDialog(
                        title = state.movie.title,
                        text = state.metadata?.overview.orEmpty(),
                        onDismiss = { showOverviewDialog = false }
                    )
                }
                if (showAddFavoriteDialog) {
                    MoveToFolderDialog(
                        folders = folders,
                        onSelect = { folderId ->
                            viewModel.addFavorite(movieId, state.movie.title, state.movie.poster, folderId)
                            showAddFavoriteDialog = false
                        },
                        onDismiss = { showAddFavoriteDialog = false }
                    )
                }
            }
        }
    }
}

/** Таблетка метаданных на экране фильма (год, длительность, рейтинг, жанр). */
@Composable
private fun DetailPill(text: String, textColor: Color, background: Color) {
    Text(
        text,
        color = textColor,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Модальное окно с полным описанием: Вверх/Вниз прокручивают текст, Назад или «Закрыть» — выход. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OverviewDialog(title: String, text: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val textFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { textFocus.safeRequestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ZenithSurface)
                .padding(28.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .focusRequester(textFocus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.DirectionDown && scroll.canScrollForward -> { scope.launch { scroll.animateScrollBy(220f) }; true }
                            e.key == Key.DirectionUp && scroll.canScrollBackward -> { scope.launch { scroll.animateScrollBy(-220f) }; true }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(scroll)
            ) {
                Text(text, color = ZenithOnSurface.copy(0.9f), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Button(onClick = onDismiss) { Text("Закрыть") }
        }
    }
}
