package com.platinum.ott.core.subtitles

// PROMPT_SUBTITLES.md, подзадача 6.
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

// Откуда пришёл текущий AI-сгенерированный текст — нужно для "явного
// индикатора в UI" (подзадача 9, не реализована).
enum class AiSource { CLOUD, LOCAL }

// Заменяет узкую версию из подзадачи 1 (там было только
// Idle/Searching/Found/NotFound для одного шага OpenSubtitles) — теперь
// охватывает весь приоритет источников целиком, как и должен оркестратор.
sealed interface AutoSubtitleState {
    data object Off : AutoSubtitleState
    data object SearchingOpenSubtitles : AutoSubtitleState
    data class UsingOpenSubtitles(val language: String) : AutoSubtitleState
    data class GeneratingAi(val source: AiSource) : AutoSubtitleState
    data class Error(val message: String) : AutoSubtitleState
}
