package com.platinum.ott.domain.model

// PROMPT_SUBTITLES.md, подзадача 1 — результат поиска OpenSubtitles,
// независим от DTO слоя (OpenSubtitlesMatchDto), как и остальные
// domain-модели проекта (см. Movie.kt/StreamVariant.kt).
data class SubtitleMatch(
    val language: String,
    val downloadUrl: String,
    val format: SubtitleFormat,
    val matchScore: Double
)

enum class SubtitleFormat { SRT, VTT }
