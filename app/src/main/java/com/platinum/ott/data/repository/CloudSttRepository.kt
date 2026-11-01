package com.platinum.ott.data.repository

import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.SttTranscriptionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File

/**
 * PROMPT_SUBTITLES.md, подзадача 6 — Android-клиент для облачного STT-
 * прокси (backend реализован в подзадаче 3, app/routers/subtitles.py на
 * zenith-backend). [Outcome.NeedsLocalFallback] — конкретно "backend
 * вернул 503, вся цепочка облачных провайдеров исчерпана/недоступна" (см.
 * stt_service.py::transcribe_with_fallback_chain на backend) — это и есть
 * сигнал "переходить на локальный Whisper" из PROMPT_SUBTITLES.md, не
 * обобщённая сетевая ошибка (та идёт в [Outcome.Error] и просто пропускает
 * конкретный сегмент, не переключает весь оставшийся пайплайн на local).
 */
class CloudSttRepository(private val api: ZenithApiService) {

    sealed interface Outcome {
        data class Success(val result: SttTranscriptionDto) : Outcome
        data object NeedsLocalFallback : Outcome
        /** 429 — слишком частые запросы (лимит backend/провайдера): стоит подождать и повторить. */
        data object RateLimited : Outcome
        data class Error(val throwable: Throwable) : Outcome
    }

    // mimeType: WAV для коротких фрагментов (обычный путь) или audio/mp4 для целого окна .m4a
    // (облегчённый путь для слабых устройств, где нет поиска речи на устройстве).
    // meta — короткая строка для логов сервера (см. SubtitleOrchestrator).
    // Если основной сервер не справился (503, 429, сеть, любая ошибка) и задан запасной адрес
    // [BACKUP_TRANSCRIBE_URL], запрос один раз повторяется там.
    suspend fun transcribe(
        wavFile: File,
        language: String,
        mimeType: String = "audio/wav",
        meta: String? = null
    ): Outcome = withContext(Dispatchers.IO) {
        val primary = send(wavFile, language, mimeType, meta, backup = false)
        if (primary is Outcome.Success || BACKUP_TRANSCRIBE_URL.isBlank()) return@withContext primary
        val secondary = send(wavFile, language, mimeType, meta, backup = true)
        // Если и запасной не ответил — возвращаем исход основного: он точнее описывает причину.
        if (secondary is Outcome.Success) secondary else primary
    }

    private suspend fun send(wavFile: File, language: String, mimeType: String, meta: String?, backup: Boolean): Outcome =
        try {
            val requestBody = wavFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", wavFile.name, requestBody)
            val languagePart = language.toRequestBody("text/plain".toMediaTypeOrNull())
            val metaPart = meta?.toRequestBody("text/plain".toMediaTypeOrNull())
            Outcome.Success(
                if (backup) api.transcribeAudioAt(BACKUP_TRANSCRIBE_URL, audioPart, languagePart, metaPart)
                else api.transcribeAudio(audioPart, languagePart, metaPart)
            )
        } catch (e: HttpException) {
            when (e.code()) {
                503 -> Outcome.NeedsLocalFallback
                429 -> Outcome.RateLimited
                else -> Outcome.Error(e)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Error(e)
        }

    companion object {
        /**
         * Адрес запасного канала — Cloudflare Worker, полностью, вместе с путём, например
         * "https://zenith-stt.ИМЯ.workers.dev/subtitles/transcribe". Пусто = запасной канал выключен.
         * Адрес не секретный (ключи хранятся в секретах Worker).
         */
        const val BACKUP_TRANSCRIBE_URL = ""
    }
}
