package com.platinum.ott.core.subtitles.whisper

import com.platinum.ott.core.subtitles.ExtractedAudioChunk
import com.platinum.ott.core.subtitles.PcmAudioDecoder
import com.platinum.ott.core.subtitles.SpeechSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WhisperSegmentResult(val startMs: Long, val endMs: Long, val text: String)

/**
 * PROMPT_SUBTITLES.md, подзадача 5 — верхнеуровневая точка входа для
 * локального Whisper, тот же уровень абстракции, что StreamAudioExtractor
 * (подзадача 2) и SileroVadSegmenter (подзадача 4): принимает уже
 * извлечённый аудио-чанк и уже найденные VAD-сегменты речи (сама VAD не
 * запускается здесь — порядок "extract → VAD → (облако ИЛИ локальный
 * Whisper)" определяет оркестратор, подзадача 6), возвращает готовые
 * подписи с текстом в тех же абсолютных координатах потока, что и облачный
 * путь (см. SttSegmentOut на zenith-backend) — оркестратору не нужно знать,
 * откуда пришёл текст, чтобы его применить.
 */
class LocalWhisperTranscriber(context: android.content.Context) {

    private val modelManager = WhisperModelManager(context)
    private var whisperContext: WhisperContext? = null
    private var loadedModelPath: String? = null

    suspend fun transcribeSegments(
        chunk: ExtractedAudioChunk,
        segments: List<SpeechSegment>,
        variant: WhisperModelVariant = WhisperModelVariant.TINY,
        language: String = "ru",
    ): Result<List<WhisperSegmentResult>> = withContext(Dispatchers.Default) {
        try {
            val modelFile = modelManager.ensureModelDownloaded(variant).getOrThrow()
            val whisper = ensureContext(modelFile.absolutePath)

            val pcmShorts = PcmAudioDecoder.decodeToMono16k(chunk.filePath)
            val pcmFloats = FloatArray(pcmShorts.size) { pcmShorts[it] / 32768f }
            val sampleRate = 16_000

            val results = mutableListOf<WhisperSegmentResult>()
            for (segment in segments) {
                val localStartMs = (segment.startMs - chunk.startPositionMs).coerceAtLeast(0)
                val localEndMs = (segment.endMs - chunk.startPositionMs).coerceAtMost(chunk.durationMs)
                val startSample = (localStartMs * sampleRate / 1000).toInt().coerceIn(0, pcmFloats.size)
                val endSample = (localEndMs * sampleRate / 1000).toInt().coerceIn(startSample, pcmFloats.size)
                if (endSample <= startSample) continue

                val slice = pcmFloats.copyOfRange(startSample, endSample)
                val nativeSegments = whisper.transcribe(slice, language)
                for (ns in nativeSegments) {
                    val text = ns.text.trim()
                    if (text.isEmpty()) continue
                    results += WhisperSegmentResult(
                        // ns.startMs/endMs локальны для СРЕЗА (slice), не для
                        // чанка целиком — сдвиг на segment.startMs (уже
                        // абсолютная координата потока, см.
                        // SileroVadSegmenter) даёт итоговую абсолютную позицию.
                        startMs = segment.startMs + ns.startMs,
                        endMs = segment.startMs + ns.endMs,
                        text = text,
                    )
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureContext(modelPath: String): WhisperContext {
        val current = whisperContext
        if (current != null && loadedModelPath == modelPath) return current
        // Смена модели (например, позже переключение TINY/BASE через
        // ползунок из подзадачи 9) — освобождаем старый нативный контекст
        // перед созданием нового: whisper_context не управляется GC,
        // без явного release() это утечка на нативной стороне.
        current?.release()
        val newContext = WhisperContext.createFromFile(modelPath)
        whisperContext = newContext
        loadedModelPath = modelPath
        return newContext
    }

    suspend fun release() {
        whisperContext?.release()
        whisperContext = null
        loadedModelPath = null
    }
}
