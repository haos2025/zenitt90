package com.platinum.ott.core.subtitles.whisper

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * PROMPT_SUBTITLES.md, подзадача 5 — обёртка над нативным контекстом
 * whisper.cpp. Тот же приём, что в официальном примере
 * (WhisperContext.java — Executors.newSingleThreadExecutor()): один и тот
 * же whisper_context нельзя вызывать из двух потоков параллельно, а мы —
 * в отличие от примера с ExecutorService+Callable — прокидываем это как
 * CoroutineDispatcher, чтобы вызывающий код (LocalWhisperTranscriber)
 * оставался в обычном suspend-стиле проекта.
 */
class WhisperContext private constructor(private var ptr: Long) {

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "whisper-native") }
        .asCoroutineDispatcher()

    suspend fun transcribe(pcm: FloatArray, language: String): List<WhisperNativeSegment> = withContext(dispatcher) {
        check(ptr != 0L) { "WhisperContext уже освобождён" }
        WhisperLib.fullTranscribe(ptr, preferredThreadCount(), pcm, language)
        val count = WhisperLib.getTextSegmentCount(ptr)
        (0 until count).map { i ->
            WhisperNativeSegment(
                // whisper.cpp отдаёт t0/t1 в ЦЕНТИСЕКУНДАХ (см. комментарий в
                // jni_bridge.cpp) — *10 переводит в мс, единую шкалу с
                // ExoPlayer.currentPosition и остальным пайплайном субтитров.
                startMs = WhisperLib.getTextSegmentT0(ptr, i) * 10,
                endMs = WhisperLib.getTextSegmentT1(ptr, i) * 10,
                text = WhisperLib.getTextSegment(ptr, i),
            )
        }
    }

    suspend fun release() = withContext(dispatcher) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    companion object {
        fun createFromFile(modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContext(modelPath)
            check(ptr != 0L) { "Не удалось загрузить модель Whisper из $modelPath" }
            return WhisperContext(ptr)
        }

        // Упрощение относительно официального примера (WhisperCpuConfig +
        // CpuInfo парсят /proc/cpuinfo на предмет числа "производительных"
        // ядер в big.LITTLE-конфигурациях) — здесь просто
        // availableProcessors(), зажатый в разумные пределы: 1 поток на
        // слабом TV-чипе будет слишком медленным, больше 4 почти не даёт
        // прироста для tiny/base моделей Whisper и может конкурировать за
        // CPU с самим воспроизведением видео.
        private fun preferredThreadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }
}

data class WhisperNativeSegment(val startMs: Long, val endMs: Long, val text: String)
