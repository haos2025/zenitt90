package com.platinum.ott.domain.usecase

import com.platinum.ott.data.repository.PlaylistRepository
import com.platinum.ott.domain.model.Movie

class GetPlaylistCatalogUseCase(private val repository: PlaylistRepository) {
    suspend fun execute(forceRefresh: Boolean = false): List<Movie> = repository.getCatalog(forceRefresh)

    // PROMPT_HOME_LOADING_FIX.md, п.4 — отдельный метод, а не замена
    // поведения execute(): execute() ещё используется SearchViewModel, где
    // плоский список серий как раз и нужен (искать по названию конкретной
    // серии), схлопывать их там было бы регрессом поиска.
    suspend fun executeGroupedBySeries(forceRefresh: Boolean = false): List<Movie> =
        repository.getCatalogGroupedBySeries(forceRefresh)
}
