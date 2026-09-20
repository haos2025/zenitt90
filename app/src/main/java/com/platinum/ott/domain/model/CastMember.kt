package com.platinum.ott.domain.model

// Раньше TmdbMetadata.cast был плоской строкой имён через запятую ("Актёры: A, B, C") —
// без фото и без роли персонажа. Этот класс приходит из TMDB credits (/movie/{id}/credits)
// и позволяет собрать нормальную карусель с фото, см. PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4.
//
// id (TMDB person id) добавлен для экрана актёра/фильмографии (подзадача 4
// прогона про фокус/оверлей/актёров) — default 0 значит "неизвестен": это
// поле сериализуется в Room как JSON (castJson в MetadataEntity), у уже
// закэшированных записей на момент этого изменения его в JSON физически
// нет — Gson при десериализации подставит дефолт 0, а не упадёт с ошибкой.
// Такая запись просто временно некликабельна в CastRow.kt, пока запись не
// обновится сама (TTL кэша — 24ч, см. TmdbRepositoryImpl.getMetadata()).
data class CastMember(val name: String, val character: String? = null, val profilePath: String? = null, val id: Int = 0)
