package com.platinum.ott.data.remote.dto

// PROMPT_SUBTITLES.md, подзадача 1. Контракт на стороне zenith-backend
// (отдельный репозиторий, здесь только клиент): GET
// subtitles/opensubtitles?title=...&year=...&langs=ru,en — backend сам
// держит ключ OpenSubtitles REST API (opensubtitles.com/api/v1, требует
// X-API-Key) и делает поиск по title+year там же, отдаёт уже
// нормализованный список кандидатов; ключ провайдера никогда не попадает
// в APK (тот же принцип, что уже применён к YOUTUBE_API_KEY). Реализация
// самого эндпоинта — отдельная сессия по zenith-backend, не входит в эту
// подзадачу.
data class OpenSubtitlesMatchDto(
    // ISO 639-1, например "ru"/"en".
    val language: String = "",
    // Прямая ссылка на файл субтитров — backend уже резолвит
    // короткоживущий download-линк OpenSubtitles сам, чтобы не хранить и
    // не обновлять его на клиенте.
    val downloadUrl: String = "",
    // "srt" | "vtt" — OpenSubtitles отдаёт оба формата, backend по
    // возможности нормализует к srt.
    val format: String = "srt",
    // 0..1 — насколько backend уверен в совпадении по названию/году
    // (матчинг не по хешу файла, см. PROMPT_SUBTITLES.md); используется
    // для выбора лучшего кандидата при нескольких результатах на один язык.
    val matchScore: Double = 0.0
)
