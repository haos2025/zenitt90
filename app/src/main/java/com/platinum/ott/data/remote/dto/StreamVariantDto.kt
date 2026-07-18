package com.platinum.ott.data.remote.dto

// Backend (GET /stream/{id}) отдаёт "голый" JSON-массив вида
// [{"quality": "1080p", "url": "..."}] — без обёртки в объект, поля
// совпадают буквально (quality/url), @SerializedName не нужен.
data class StreamVariantDto(val quality: String = "", val url: String = "")
