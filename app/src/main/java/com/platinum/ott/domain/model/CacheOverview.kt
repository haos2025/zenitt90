package com.platinum.ott.domain.model

/**
 * Снимок состояния всех независимых кэшей приложения — для экрана
 * управления кэшем (PROMPT_CACHE_MANAGEMENT.md).
 *
 * catalogEntries/playlistEntries/metadataEntries — количество записей в
 * соответствующей таблице Room, не байты: точный размер одной таблицы в
 * общей SQLite-базе (вместе с избранным, плагинами и т.д.) недоступен
 * штатными средствами Room без dbstat.
 *
 * posterCacheBytes/crashLogBytes — честный размер на диске, это отдельные
 * папки (Coil DiskCache и filesDir/crash_logs), там подсчёт байт простой.
 */
data class CacheOverview(
    val catalogEntries: Int,
    val playlistEntries: Int,
    val metadataEntries: Int,
    val posterCacheBytes: Long,
    val crashLogBytes: Long,
    val crashLogCount: Int
)
