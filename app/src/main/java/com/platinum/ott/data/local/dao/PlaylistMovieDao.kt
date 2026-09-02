package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.PlaylistMovieEntity

@Dao
interface PlaylistMovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<PlaylistMovieEntity>)
    @Query("SELECT * FROM playlist_movies ORDER BY title ASC")
    suspend fun getAll(): List<PlaylistMovieEntity>
    @Query("SELECT * FROM playlist_movies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaylistMovieEntity?
    @Query("SELECT MAX(cachedAt) FROM playlist_movies")
    suspend fun getLatestCacheTime(): Long?
    @Query("DELETE FROM playlist_movies")
    suspend fun clearAll(): Int
    // Добавлено для экрана управления кэшем (PROMPT_CACHE_MANAGEMENT.md) —
    // точный размер в байтах одной таблицы Room недоступен штатными
    // средствами, показываем количество записей.
    @Query("SELECT COUNT(*) FROM playlist_movies")
    suspend fun getCount(): Int

    // Ниже — добавлено задачей "Источники" (PROMPT_SOURCES_SCREEN.md).

    @Query("SELECT * FROM playlist_movies WHERE sourceId IN (:sourceIds) ORDER BY title ASC")
    suspend fun getAllForSources(sourceIds: List<String>): List<PlaylistMovieEntity>

    @Query("DELETE FROM playlist_movies WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM playlist_movies WHERE sourceId = :sourceId")
    suspend fun getCountBySource(sourceId: String): Int

    @Query("SELECT MAX(cachedAt) FROM playlist_movies WHERE sourceId = :sourceId")
    suspend fun getLatestCacheTimeForSource(sourceId: String): Long?

    // Используется один раз — миграцией существующего пользователя
    // (PlaylistSourceRepository.migrateLegacySourceIfNeeded()), чтобы
    // проставить sourceId уже закэшированным строкам от старого
    // единственного источника задним числом, не перекачивая их заново.
    @Query("UPDATE playlist_movies SET sourceId = :sourceId WHERE sourceId IS NULL")
    suspend fun assignSourceIdWhereNull(sourceId: String)
}
