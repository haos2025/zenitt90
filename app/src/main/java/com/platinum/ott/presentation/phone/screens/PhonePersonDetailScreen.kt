package com.platinum.ott.presentation.phone.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
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
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.PersonFilmographyRow
import com.platinum.ott.presentation.screens.person.PersonDetailViewModel
import com.platinum.ott.presentation.screens.person.PersonUiState
import com.platinum.ott.ui.theme.*
import kotlinx.coroutines.launch

private val photoWidth = 140.dp
private val photoHeight = 210.dp

/**
 * Телефонная версия экрана актёра — те же данные, что и в
 * PersonDetailScreen.kt (TV), тумблер биографии — обычный clickable
 * (тач, не пульт), фото сверху вместо ряда слева (уже колонки, портретная
 * фотография актёра будет слишком узкой рядом с текстом на телефоне).
 */
@Composable
fun PhonePersonDetailScreen(personId: Int, navController: NavHostController, viewModel: PersonDetailViewModel = hiltViewModel()) {
    LaunchedEffect(personId) { viewModel.load(personId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingM)) {
        when (val state = uiState) {
            is PersonUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            is PersonUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
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
                val movies = remember(profile.filmography) { profile.filmography.filter { it.mediaType == "movie" } }
                val series = remember(profile.filmography) { profile.filmography.filter { it.mediaType == "tv" } }
                val onCreditClick: (com.platinum.ott.domain.model.PersonCreditItem) -> Unit = { credit ->
                    scope.launch {
                        val foundId = viewModel.findInCatalog(credit)
                        if (foundId != null) navController.navigate("detail/$foundId")
                        else Toast.makeText(context, "«${credit.title}» не найден в каталоге", Toast.LENGTH_SHORT).show()
                    }
                }

                Box(
                    modifier = Modifier.align(Alignment.CenterHorizontally).width(photoWidth).height(photoHeight)
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
                            modifier = Modifier.align(Alignment.Center).size(72.dp))
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                Text(profile.name, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                profile.department?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                profile.birthday?.let { birthday ->
                    val line = if (profile.deathday != null) "$birthday — ${profile.deathday}" else birthday
                    Text("Дата рождения: $line", color = Color.White.copy(0.8f))
                }
                profile.placeOfBirth?.let { Text("Место рождения: $it", color = Color.White.copy(0.8f)) }
                profile.biography?.let { bio ->
                    var isBioExpanded by remember(personId) { mutableStateOf(false) }
                    var isBioExpandable by remember(personId) { mutableStateOf(false) }
                    Text(
                        bio, color = Color.White.copy(0.8f),
                        modifier = Modifier.padding(top = ZenithDimens.paddingS),
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!isBioExpanded) isBioExpandable = it.hasVisualOverflow }
                    )
                    if (isBioExpandable) {
                        Text(
                            if (isBioExpanded) "Свернуть" else "Показать полностью",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = ZenithDimens.paddingXS).clickable { isBioExpanded = !isBioExpanded }
                        )
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                if (movies.isNotEmpty()) {
                    PersonFilmographyRow("Фильмы", movies, onItemClick = onCreditClick)
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                }
                if (series.isNotEmpty()) {
                    PersonFilmographyRow("Сериалы", series, onItemClick = onCreditClick)
                }
            }
        }
    }
}
