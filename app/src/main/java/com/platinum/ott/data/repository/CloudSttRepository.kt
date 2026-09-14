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
        data class Error(val throwable: Throwable) : Outcome
    }

    suspend fun transcribe(wavFile: File, language: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val requestBody = wavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", wavFile.name, requestBody)
            val languagePart = language.toRequestBody("text/plain".toMediaTypeOrNull())
            Outcome.Success(api.transcribeAudio(audioPart, languagePart))
        } catch (e: HttpException) {
            if (e.code() == 503) Outcome.NeedsLocalFallback else Outcome.Error(e)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}
