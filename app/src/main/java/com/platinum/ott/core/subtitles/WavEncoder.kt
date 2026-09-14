package com.platinum.ott.core.subtitles

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PROMPT_SUBTITLES.md, подзадача 6 — пишет минимальный WAV-контейнер
 * (44-байтный заголовок + сырые PCM-сэмплы), без какой-либо кодек-
 * библиотеки: для 16-битного PCM WAV — это просто заголовок поверх уже
 * готовых сэмплов, кодировать нечего.
 *
 * Нужен, чтобы отправить в облачный STT (подзадача 3) только КОНКРЕТНЫЙ
 * VAD-сегмент речи (подзадача 4), а не весь 5-минутный чанк от
 * StreamAudioExtractor (подзадача 2) целиком — тот же PCM, что уже
 * декодирован через PcmAudioDecoder, здесь просто нарезается по границам
 * сегмента и оборачивается в контейнер, который примет любой HTTP STT-
 * провайдер (Groq и OpenAI-совместимые провайдеры принимают wav "из
 * коробки", без промежуточного кодирования в AAC/mp3).
 */
object WavEncoder {
    private const val SAMPLE_RATE = 16_000
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1

    fun encode(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // размер fmt-блока для PCM
        buffer.putShort(1) // формат 1 = PCM без сжатия
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) // byte rate
        buffer.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // block align
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (sample in pcm) buffer.putShort(sample)
        return buffer.array()
    }
}
