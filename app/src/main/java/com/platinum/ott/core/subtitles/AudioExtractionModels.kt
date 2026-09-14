package com.platinum.ott.core.subtitles

// PROMPT_SUBTITLES.md, подзадача 2 — извлечение аудиодорожки из потока
// ОТДЕЛЬНО от воспроизведения (архитектурное требование промта). Модель
// одна для VOD и живых каналов, разница только в том, кто и как формирует
// startPositionMs/durationMs (это дело будущего оркестратора, подзадача 6):
// для VOD — скользящее окно 5 минут вперёд от позиции плеера; для живых
// каналов — короткий чанк от текущего момента (нет отдельного "будущего",
// эфира ещё не было).

data class AudioExtractionRequest(
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    // Для VOD — позиция в собственном таймлайне контента (та же шкала, что
    // ExoPlayer.currentPosition в PlayerViewModel). Для живых каналов
    // семантика другая (см. известное ограничение в StreamAudioExtractor.kt)
    // и пока не проверена реальным устройством — см. TODO там же.
    val startPositionMs: Long,
    val durationMs: Long
)

data class ExtractedAudioChunk(
    val filePath: String,
    val startPositionMs: Long,
    val durationMs: Long,
    val mimeType: String
)
