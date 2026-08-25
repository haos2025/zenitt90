package com.platinum.ott.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.RecentSearchPreferences
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val results: List<Movie>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

// Раньше поиск существовал только как MovieSearchProvider для системного
// поиска Android TV — в самом приложении не было ни экрана, ни способа
// ввести запрос текстом. searchMoviesUseCase был готов в ServiceLocator с
// самого начала, но НИКЕМ не вызывался.
//
// searchMoviesUseCase ходит только в backend-каталог — у PlaylistRepository
// (M3U/Xtream) нет текстового поиска по API. Чтобы плейлист не выпадал из
// поиска совсем, дополнительно фильтруем уже загруженный на телефон/TV
// список плейлиста по подстроке в названии — тот же getPlaylistCatalogUseCase,
// что и HomeViewModel использует для главного экрана.
@HiltViewModel
class SearchViewModel @Inject constructor(
    sessionGraph: SessionGraph,
    private val recentSearchPrefs: RecentSearchPreferences
) : ViewModel() {
    private val searchMovies = sessionGraph.searchMoviesUseCase
    private val getPlaylistCatalog = sessionGraph.getPlaylistCatalogUseCase

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _recentSearches = MutableStateFlow(recentSearchPrefs.getRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches

    private var cachedPlaylist: List<Movie>? = null
    private var cachedPlaylistTimestamp: Long = 0L

    // Тот же TTL, что и у PlaylistRepository.REFRESH_TTL_MS (константа
    // private там, значение продублировано здесь намеренно — раньше кэш
    // тут не имел срока жизни вообще и держался весь жизненный цикл экрана).
    private val playlistCacheTtlMs = 60 * 60 * 1000L

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) { _uiState.value = SearchUiState.Idle; return@collectLatest }
                _uiState.value = SearchUiState.Loading
                runSearch(q)
            }
        }
    }

    fun onQueryChange(newQuery: String) { _query.value = newQuery }

    fun removeRecentSearch(entry: String) {
        recentSearchPrefs.removeRecentSearch(entry)
        _recentSearches.value = recentSearchPrefs.getRecentSearches()
    }

    fun clearRecentSearches() {
        recentSearchPrefs.clearRecentSearches()
        _recentSearches.value = recentSearchPrefs.getRecentSearches()
    }

    private suspend fun runSearch(q: String) {
        // Записываем в историю по факту реального поиска (сюда попадаем
        // только после debounce+distinctUntilChanged), а не на каждое
        // изменение текстового поля — иначе список забился бы обрывками.
        recentSearchPrefs.addRecentSearch(q)
        _recentSearches.value = recentSearchPrefs.getRecentSearches()

        val now = System.currentTimeMillis()
        if (cachedPlaylist == null || now - cachedPlaylistTimestamp > playlistCacheTtlMs) {
            cachedPlaylist = try { getPlaylistCatalog.execute() } catch (_: Exception) { emptyList() }
            cachedPlaylistTimestamp = now
        }

        val playlist = cachedPlaylist.orEmpty()
        val exactMatches = playlist.filter { it.title.contains(q, ignoreCase = true) }
        // Нечёткое сравнение — только вдогонку к точному, не вместо него:
        // опечатка ("интерстелар" вместо "Интерстеллар") иначе дала бы
        // пустой список. Бэкенд-поиск сюда не входит — источники ищут сами.
        val exactIds = exactMatches.map { it.id }.toSet()
        val fuzzyMatches = playlist
            .asSequence()
            .filter { it.id !in exactIds }
            .filter { isFuzzyMatch(query = q, title = it.title) }
            .toList()
        val playlistMatches = exactMatches + fuzzyMatches

        searchMovies.execute(q)
            .onSuccess { backendMatches ->
                val combined = (backendMatches + playlistMatches).distinctBy { it.id }
                _uiState.value = if (combined.isEmpty()) SearchUiState.Success(emptyList()) else SearchUiState.Success(combined)
            }
            .onFailure { error ->
                // Backend недоступен, но локальный плейлист может дать результат —
                // тот же принцип отказоустойчивости, что в HomeViewModel.
                if (playlistMatches.isNotEmpty()) _uiState.value = SearchUiState.Success(playlistMatches)
                else _uiState.value = SearchUiState.Error(error.message ?: "Ошибка поиска")
            }
    }

    // Порог расстояния Левенштейна растёт с длиной запроса — на коротких
    // словах даже одна опечатка ощутимо меняет смысл, на длинных допустимо
    // больше отклонений. Сравнивается запрос с каждым отдельным словом
    // названия (не всей строкой целиком) — иначе короткий запрос почти
    // никогда не окажется "близко" к длинному многословному названию по
    // Левенштейну, даже если совпадает одно из слов.
    private fun isFuzzyMatch(query: String, title: String): Boolean {
        val q = query.trim().lowercase()
        if (q.length < 3) return false // на 1-2 символах нечёткое сравнение только шумит
        val threshold = when {
            q.length <= 4 -> 1
            q.length <= 8 -> 2
            else -> 3
        }
        return title.split(Regex("\\s+")).any { word ->
            val w = word.lowercase()
            w.isNotEmpty() && levenshtein(q, w) <= threshold
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        // Одна строка на предыдущую строку таблицы — сама задача маленькая
        // (слова из истории/названий, обычно 5-15 символов), полная
        // m×n-матрица не нужна.
        var prevRow = IntArray(n + 1) { it }
        var currRow = IntArray(n + 1)
        for (i in 1..m) {
            currRow[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currRow[j] = minOf(
                    currRow[j - 1] + 1,      // вставка
                    prevRow[j] + 1,          // удаление
                    prevRow[j - 1] + cost    // замена
                )
            }
            val tmp = prevRow
            prevRow = currRow
            currRow = tmp
        }
        return prevRow[n]
    }
}
