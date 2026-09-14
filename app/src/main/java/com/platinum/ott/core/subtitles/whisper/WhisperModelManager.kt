package com.platinum.ott.core.subtitles.whisper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class WhisperModelVariant(val fileName: String, val downloadUrl: String) {
    // "Ползунок скорость/точность" (PROMPT_SUBTITLES.md, подзадача 9) ляжет
    // ровно на выбор между этими двумя вариантами — TINY (быстрее, ~75МБ)
    // при приоритете скорости, BASE (~142МБ) при приоритете точности; сама
    // настройка ещё не реализована, здесь только сами варианты модели.
    TINY("ggml-tiny.bin", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"),
    BASE("ggml-base.bin", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"),
}

/**
 * PROMPT_SUBTITLES.md, подзадача 5 — "решить на сессии, бандлить в APK или
 * скачивать при первом использовании". Решение этой сессии: СКАЧИВАТЬ, не
 * бандлить — даже tiny-модель (~75МБ) утяжеляла бы APK для всех
 * пользователей, включая тех, кто никогда не включит автосубтитры.
 *
 * Файл живёт в filesDir (не cacheDir — систему может очистить cache в
 * любой момент, а повторное скачивание 75-140МБ не должно происходить
 * неожиданно посреди просмотра) — виден существующему экрану управления
 * кэшем (PROMPT_CACHE_MANAGEMENT.md) как обычное место на диске, но сама
 * интеграция с тем экраном (показать/удалить модель оттуда) в эту
 * подзадачу не входит.
 */
class WhisperModelManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File by lazy {
        File(context.filesDir, "whisper_models").apply { mkdirs() }
    }

    suspend fun ensureModelDownloaded(variant: WhisperModelVariant): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, variant.fileName)
        // Простая проверка "файл существует и не пустой" — без сверки
        // контрольной суммы (SHA не захардкожен в этой версии). Если файл
        // повредился на середине предыдущей закачки, это НЕ будет
        // обнаружено автоматически — стоит доработать позже, не сделано
        // сейчас ради ограничения объёма подзадачи.
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext Result.success(targetFile)
        }
        try {
            val request = Request.Builder().url(variant.downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Скачивание модели не удалось: HTTP ${response.code}")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Пустое тело ответа при скачивании модели"))
                val tempFile = File(modelsDir, "${variant.fileName}.part")
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.delete()
                    return@withContext Result.failure(IllegalStateException("Не удалось переименовать скачанный файл модели"))
                }
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
