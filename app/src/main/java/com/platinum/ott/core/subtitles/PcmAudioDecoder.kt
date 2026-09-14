package com.platinum.ott.core.subtitles

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * PROMPT_SUBTITLES.md, подзадача 4 (VAD) — и Silero VAD, и, позже,
 * локальный Whisper (подзадача 5) требуют один и тот же формат входа:
 * 16кГц, моно, 16-битный PCM. `StreamAudioExtractor` (подзадача 2) отдаёт
 * компактный AAC-файл, а не сырой PCM (см. обоснование в
 * StreamAudioExtractor.kt — этот формат экономнее для сети/облака) —
 * данный декодер восстанавливает PCM из него на лету, только на время
 * самого VAD/STT-прохода, ничего не кэшируется на диск.
 *
 * НЕ ПРОВЕРЕНО на реальном устройстве — низкоуровневый синхронный цикл
 * MediaCodec, у разных производителей TV-приставок исторически
 * встречаются особенности синхронного API (тот же принцип, что и в
 * COMPATIBILITY.md про то, что нельзя проверить без реальной сборки/
 * устройства).
 */
object PcmAudioDecoder {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10_000L

    /** Декодирует аудиодорожку [filePath] в моно 16кГц 16-битный PCM. */
    fun decodeToMono16k(filePath: String): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("Аудиодорожка не найдена: $filePath")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            return try {
                codec.configure(format, null, null, 0)
                codec.start()
                val monoAtSourceRate = decodeLoop(extractor, codec, channelCount)
                resampleLinear(monoAtSourceRate, sourceSampleRate, TARGET_SAMPLE_RATE)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeLoop(extractor: MediaExtractor, codec: MediaCodec, channelCount: Int): ShortArray {
        val buffer = GrowableShortBuffer()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                val shortBuffer = outputBuffer.asShortBuffer()
                val frameCount = bufferInfo.size / 2 / channelCount
                for (f in 0 until frameCount) {
                    var sum = 0
                    for (c in 0 until channelCount) sum += shortBuffer.get(f * channelCount + c)
                    buffer.add((sum / channelCount).toShort())
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        return buffer.toShortArray()
    }

    private fun resampleLinear(input: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        if (input.isEmpty() || sourceRate == targetRate) return input
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val idx = srcPos.toInt().coerceIn(0, input.size - 1)
            val nextIdx = (idx + 1).coerceIn(0, input.size - 1)
            val frac = srcPos - idx
            output[i] = (input[idx] + (input[nextIdx] - input[idx]) * frac).toInt().toShort()
        }
        return output
    }

    // List<Short>/ArrayList<Short> в Kotlin боксят каждый элемент в
    // Short-объект — для 5-минутного чанка на исходной частоте (может быть
    // 48кГц, это ~14 млн сэмплов) это лишние десятки МБ мусора для GC на
    // слабом TV-чипе; простой массив с ручным ростом ёмкости этого избегает.
    private class GrowableShortBuffer(initialCapacity: Int = 1 shl 16) {
        private var array = ShortArray(initialCapacity)
        private var size = 0
        fun add(value: Short) {
            if (size == array.size) array = array.copyOf(array.size * 2)
            array[size++] = value
        }
        fun toShortArray(): ShortArray = array.copyOf(size)
    }
}
