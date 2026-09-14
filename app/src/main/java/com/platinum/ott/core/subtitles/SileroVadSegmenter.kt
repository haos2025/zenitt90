package com.platinum.ott.core.subtitles

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * PROMPT_SUBTITLES.md, подзадача 4 — "Нарезка по паузам речи (VAD)":
 * лёгкая модель (Silero VAD, на порядки легче Whisper) режет аудио-чанк из
 * `StreamAudioExtractor` (подзадача 2) на сегменты речи, экономя квоту
 * будущих облачных STT-запросов (подзадача 3) — тишина никуда не
 * отправляется — и давая аккуратные границы без обрывов посреди фразы.
 *
 * Модель на порядки легче Whisper, поэтому здесь простой синхронный проход
 * по всему чанку — 5 минут @16кГц это ≈ 9375 фреймов по 512 сэмплов,
 * однопоточный инференс такого размера не проблема даже на слабом TV-чипе
 * согласно самому промту.
 *
 * ЧТО НЕ ПРОВЕРЕНО и требует ручной проверки на реальном устройстве/CI
 * (тот же принцип, что в COMPATIBILITY.md — есть вещи, которые нельзя
 * проверить без реальной среды сборки):
 * 1. Точная форма/имена входов-выходов ONNX-графа ("input"/"state"/"sr" на
 *    входе, "output"/"stateN" на выходе, состояние [2,1,128]) —
 *    задокументированы по официальному экспорту Silero VAD v6.x
 *    (snakers4/silero-vad), но без реального запуска ORT здесь это
 *    невозможно перепроверить; более старые версии модели (v3/v4) имели
 *    другую сигнатуру ("h0"/"c0" раздельно, [2,1,64]) — если бандлится
 *    старая версия модели, потребуется другой код инференса.
 * 2. Как именно `OnnxTensor.value` мапится на вложенные Kotlin-массивы для
 *    выходов — предположение основано на типовом поведении Java API ORT
 *    (форма тензора → вложенные примитивные массивы той же размерности).
 *
 * ТРЕБУЕТСЯ РУКАМИ (бинарный артефакт, не может быть сгенерирован в этой
 * сессии — нет доступа к GitHub-релизам с бинарными файлами из этого
 * окружения): положить официальный экспорт модели
 * (github.com/snakers4/silero-vad, файл src/silero_vad/data/silero_vad.onnx,
 * ~2МБ) в `app/src/main/assets/models/silero_vad.onnx` до того, как этот
 * код будет реально запущен — до тех пор ensureSession() упадёт с
 * FileNotFoundException при первом вызове (не ошибка компиляции).
 */
@Suppress("UNCHECKED_CAST")
class SileroVadSegmenter(private val context: Context) {

    private companion object {
        const val MODEL_ASSET_PATH = "models/silero_vad.onnx"
        const val SAMPLE_RATE = 16_000L
        const val FRAME_SIZE = 512 // 32мс @ 16кГц — фиксировано архитектурой модели
        const val STATE_SIZE = 128
    }

    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    private fun ensureSession(): OrtSession {
        session?.let { return it }
        val modelBytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
        return ortEnvironment.createSession(modelBytes).also { session = it }
    }

    suspend fun detectSpeechSegments(
        chunk: ExtractedAudioChunk,
        config: VadConfig = VadConfig()
    ): Result<List<SpeechSegment>> = withContext(Dispatchers.Default) {
        try {
            val pcm = PcmAudioDecoder.decodeToMono16k(chunk.filePath)
            val probabilities = runInference(pcm)
            val localSegments = segmentFromProbabilities(probabilities, pcm.size, config)
            // Из локальных координат чанка в абсолютные координаты потока
            // (та же шкала, что ExoPlayer.currentPosition) — чанк начинается
            // с chunk.startPositionMs.
            val absolute = localSegments.map {
                SpeechSegment(it.startMs + chunk.startPositionMs, it.endMs + chunk.startPositionMs)
            }
            Result.success(absolute)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun runInference(pcm: ShortArray): FloatArray {
        val ortSession = ensureSession()
        val frameCount = pcm.size / FRAME_SIZE
        val probabilities = FloatArray(frameCount)
        // LSTM-состояние переносится между фреймами ОДНОГО чанка; каждый
        // новый вызов detectSpeechSegments — независимый аудио-файл от
        // StreamAudioExtractor, поэтому состояние всегда стартует с нулей
        // (небольшая потеря точности на первых ~десятках мс чанка, не
        // критично при окне в несколько минут).
        val state = FloatArray(2 * 1 * STATE_SIZE)

        for (i in 0 until frameCount) {
            val frame = FloatArray(FRAME_SIZE)
            for (j in 0 until FRAME_SIZE) {
                // int16 -> [-1, 1], стандартная нормализация для входа Silero VAD.
                frame[j] = pcm[i * FRAME_SIZE + j] / 32768f
            }
            OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(frame), longArrayOf(1, FRAME_SIZE.toLong())).use { input ->
                OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(state), longArrayOf(2, 1, STATE_SIZE.toLong())).use { st ->
                    OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(longArrayOf(SAMPLE_RATE)), longArrayOf()).use { sr ->
                        ortSession.run(mapOf("input" to input, "state" to st, "sr" to sr)).use { results ->
                            val outputProb = (results.get("output").get().value as Array<FloatArray>)[0][0]
                            probabilities[i] = outputProb
                            val newState = results.get("stateN").get().value as Array<Array<FloatArray>>
                            var idx = 0
                            for (a in newState) for (b in a) for (v in b) state[idx++] = v
                        }
                    }
                }
            }
        }
        return probabilities
    }

    private fun segmentFromProbabilities(probs: FloatArray, totalSamples: Int, config: VadConfig): List<SpeechSegment> {
        if (probs.isEmpty()) return emptyList()
        val msPerFrame = FRAME_SIZE * 1000.0 / SAMPLE_RATE
        val totalMs = (totalSamples * 1000L / SAMPLE_RATE)

        data class RawRange(var startFrame: Int, var endFrame: Int)
        val raw = mutableListOf<RawRange>()
        var current: RawRange? = null
        var silenceRunMs = 0.0

        for (i in probs.indices) {
            val isSpeech = probs[i] >= config.speechThreshold
            if (isSpeech) {
                silenceRunMs = 0.0
                val c = current
                if (c == null) current = RawRange(i, i) else c.endFrame = i
            } else if (current != null) {
                silenceRunMs += msPerFrame
                if (silenceRunMs >= config.minSilenceDurationMs) {
                    raw += current!!
                    current = null
                    silenceRunMs = 0.0
                }
                // Пауза внутри фразы короче порога — сегмент не закрываем,
                // просто ждём следующего речевого фрейма (или окончательного
                // закрытия по min_silence, см. выше).
            }
        }
        current?.let { raw += it }

        val paddingFrames = (config.paddingMs / msPerFrame).toInt().coerceAtLeast(0)
        val minSpeechFrames = (config.minSpeechDurationMs / msPerFrame).toInt()

        val padded = raw
            .filter { (it.endFrame - it.startFrame + 1) >= minSpeechFrames }
            .map { seg ->
                val startFrame = (seg.startFrame - paddingFrames).coerceAtLeast(0)
                val endFrame = (seg.endFrame + paddingFrames).coerceAtMost(probs.size - 1)
                SpeechSegment(
                    startMs = (startFrame * msPerFrame).toLong(),
                    endMs = (((endFrame + 1) * msPerFrame).toLong()).coerceAtMost(totalMs)
                )
            }
        if (padded.isEmpty()) return emptyList()

        // Паддинг мог свести соседние сегменты внахлёст (сознательно, см.
        // VadConfig.paddingMs) — сливаем пересекающиеся/смежные, чтобы не
        // гонять на STT дважды один и тот же кусок как "два сегмента".
        val merged = mutableListOf(padded.first())
        for (seg in padded.drop(1)) {
            val last = merged.last()
            if (seg.startMs <= last.endMs) {
                merged[merged.size - 1] = SpeechSegment(last.startMs, maxOf(last.endMs, seg.endMs))
            } else {
                merged += seg
            }
        }
        return merged
    }

    fun release() {
        session?.close()
        session = null
    }
}
