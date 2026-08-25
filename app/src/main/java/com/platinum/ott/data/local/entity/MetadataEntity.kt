package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val contentId: String, val tmdbId: Int? = null,
    val posterPath: String? = null, val backdropPath: String? = null,
    val overview: String? = null, val voteAverage: Double? = null,
    val genres: String? = null, val trailerUrl: String? = null,
    // cast (плоская строка имён) оставлен нетронутым столбцом ради обратной
    // совместимости со старыми закэшированными строками — Room-миграция
    // добавляет столбец, не удаляет (SQLite не умеет удалять столбец без
    // пересоздания таблицы, см. ZenithDatabase.kt MIGRATION_10_11). Больше
    // не заполняется — реальные данные теперь в castJson.
    val cast: String? = null,
    // Список CastMember (см. domain/model/CastMember.kt), сериализованный
    // Gson-ом в TmdbRepositoryImpl — Gson уже есть в зависимостях проекта
    // (используется в RetrofitFactory), отдельный Room TypeConverter не
    // заводится ради одного поля. У старых закэшированных строк это NULL —
    // просто не показывать карусель актёров для них, пока не перезапросится
    // заново (TTL 24ч в getMetadata(), тот же принцип, что и в
    // MIGRATION_5_6/6_7/7_8/9_10 — ничего принудительно не мигрируем).
    val castJson: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
