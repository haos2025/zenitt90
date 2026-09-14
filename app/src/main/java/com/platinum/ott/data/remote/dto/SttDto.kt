package com.platinum.ott.data.remote.dto

// PROMPT_SUBTITLES.md, подзадача 6 — контракт под POST subtitles/transcribe
// на zenith-backend (реализован в подзадаче 3, см.
// app/models/subtitles_schemas.py::SttTranscriptionOut/SttSegmentOut на
// backend-стороне). camelCase — как и OpenSubtitlesMatchDto: RetrofitFactory
// использует голый GsonConverterFactory без field naming policy.
data class SttSegmentDto(
    val startMs: Long = 0,
    val endMs: Long = 0,
    val text: String = ""
)

data class SttTranscriptionDto(
    val text: String = "",
    val segments: List<SttSegmentDto> = emptyList(),
    val provider: String = "",
    val usedFallback: Boolean = false
)
