package com.platinum.ott.provider

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.runBlocking

// Раньше query() при любом запросе от двух символов возвращал одну
// выдуманную строку "Search result for: $q" — ни один реальный фильм
// никогда не находился, это была декорация без функциональности.
//
// Важное ограничение (честно, а не молчком): ServiceLocator.searchMoviesUseCase
// ходит только в каталог zenith-backend — плейлисты M3U/Xtream (то, что
// добавляется через SetupScreen) в этом поиске не участвуют, у них нет
// отдельного текстового поиска в PlaylistRepository. Значит глобальный
// поиск Android TV найдёт только то, что есть в бэкенд-каталоге.
class MovieSearchProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val q = selectionArgs?.firstOrNull() ?: uri.lastPathSegment ?: ""
        val columns = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
        )
        val cursor = MatrixCursor(columns)
        if (q.isBlank() || q.length < 2) return cursor
        // onCreate() ContentProvider'а система вызывает ДО Application.onCreate()
        // (см. жизненный цикл процесса в документации) — если поиск случится
        // аномально рано, ServiceLocator ещё может быть не инициализирован.
        // Пустая выдача лучше краша всего системного поиска на TV.
        try {
            val movies = runBlocking { ServiceLocator.searchMoviesUseCase.execute(q) }.getOrDefault(emptyList())
            movies.take(20).forEachIndexed { index, movie ->
                // zenith://detail?id=... — тот же deep-link формат, что уже
                // обрабатывается MainActivity.kt для zenith://player, просто
                // на карточку фильма, а не сразу в проигрывание: после
                // поиска логичнее сначала увидеть описание, чем чтобы TV
                // сразу начал воспроизведение без подтверждения.
                cursor.addRow(arrayOf(
                    index.toLong(),
                    movie.title,
                    movie.year.takeIf { it > 0 }?.toString() ?: "",
                    "zenith://detail?id=${movie.id}"
                ))
            }
        } catch (_: Exception) { /* поиск недоступен (например, ServiceLocator ещё не готов) — пустая выдача */ }
        return cursor
    }

    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
