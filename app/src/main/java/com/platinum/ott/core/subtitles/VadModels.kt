package com.platinum.ott.core.subtitles

// PROMPT_SUBTITLES.md, подзадача 4. Границы уже в абсолютных координатах
// потока (та же шкала, что ExoPlayer.currentPosition в PlayerViewModel) —
// сдвиг из локальных координат чанка делает SileroVadSegmenter сам.
data class SpeechSegment(val startMs: Long, val endMs: Long)

data class VadConfig(
    val speechThreshold: Float = 0.5f,
    val minSpeechDurationMs: Long = 250L,
    val minSilenceDurationMs: Long = 400L,
    // "Внахлёст между отправляемыми кусками звука" (PROMPT_SUBTITLES.md) —
    // паддинг на границах каждого сегмента. Если после паддинга соседние
    // сегменты пересекаются (короткая пауза между ними), SileroVadSegmenter
    // сливает их в один — нет смысла отправлять на STT два перекрывающихся
    // куска отдельно, если так и так получится один сплошной сегмент.
    val paddingMs: Long = 250L
)
