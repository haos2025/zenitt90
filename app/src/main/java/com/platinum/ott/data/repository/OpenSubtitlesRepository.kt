package com.platinum.ott.data.repository

import com.platinum.ott.data.remote.ZenithApiService
import com.platinum.ott.data.remote.dto.OpenSubtitlesMatchDto
import com.platinum.ott.domain.model.SubtitleFormat
import com.platinum.ott.domain.model.SubtitleMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PROMPT_SUBTITLES.md, подзадача 1 — VOD, шаг "лёгкая проверка
 * OpenSubtitles" из общего приоритета источников (раздел "Приоритет
 * источников" в промте: если нашлось — использовать, если нет — AI-путь,
 * который будет добавлен последующими подзадачами). Сам поиск и ключ
 * провайдера — на zenith-backend (ZenithApiService.searchOpenSubtitles
 * уже ходит на задеплоенный backend, см. RetrofitFactory.ZENITH_BASE_URL);
 * здесь только выбор лучшего кандидата и защита от сетевых ошибок —
 * отсутствие матча или сбой backend не должны прерывать переключение
 * "Субтитры" в плеере, это штатный повод перейти на AI-путь, поэтому
 * любая ошибка тут превращается в null, не в исключение.
 */
class OpenSubtitlesRepository(private val api: ZenithApiService) {

    /**
     * @param preferredLanguages порядок предпочтения языков, первый
     * совпавший побеждает при равном matchScore — по умолчанию русский,
     * затем английский (проект ориентирован на русскоязычную аудиторию,
     * см. README.md/ROADMAP.md).
     */
    suspend fun findBestMatch(
        title: String,
        year: Int,
        preferredLanguages: List<String> = listOf("ru", "en")
    ): SubtitleMatch? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val results = api.searchOpenSubtitles(title, year, preferredLanguages.joinToString(","))
            if (results.isEmpty()) return@withContext null
            // Сначала по позиции языка в preferredLanguages (ru раньше en),
            // внутри одного языка — по matchScore, который отдаёт backend
            // (учитывает неточность матчинга по названию/году, см.
            // комментарий в OpenSubtitlesMatchDto).
            results
                .sortedWith(
                    compareBy<OpenSubtitlesMatchDto> {
                        val idx = preferredLanguages.indexOf(it.language)
                        if (idx == -1) preferredLanguages.size else idx
                    }.thenByDescending { it.matchScore }
                )
                .firstOrNull()
                ?.toDomain()
        } catch (_: Exception) {
            null
        }
    }

    private fun OpenSubtitlesMatchDto.toDomain() = SubtitleMatch(
        language = language,
        downloadUrl = downloadUrl,
        format = if (format.equals("vtt", ignoreCase = true)) SubtitleFormat.VTT else SubtitleFormat.SRT,
        matchScore = matchScore
    )
}
