package com.platinum.ott.domain.usecase

import com.platinum.ott.data.repository.OpenSubtitlesRepository
import com.platinum.ott.domain.model.SubtitleMatch

// PROMPT_SUBTITLES.md, подзадача 1.
class SearchOpenSubtitlesUseCase(private val repo: OpenSubtitlesRepository) {
    suspend fun execute(title: String, year: Int): SubtitleMatch? = repo.findBestMatch(title, year)
}
