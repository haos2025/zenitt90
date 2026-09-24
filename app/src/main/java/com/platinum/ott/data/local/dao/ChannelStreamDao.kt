package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.ChannelStreamEntity

@Dao
interface ChannelStreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stream: ChannelStreamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(streams: List<ChannelStreamEntity>)

    @Query("SELECT * FROM channel_streams WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelStreamEntity?

    // Для fallback при воспроизведении: все стримы канала, живые сначала
    // (по lastCheckStatus), внутри — по приоритету. "alive" < "dead" < "unknown"
    // алфавитно не подходит, поэтому сортировка по приоритету здесь только
    // предварительная — реальный выбор живого стрима делает use-case на
    // Kotlin-стороне (подзадача "fallback при воспроизведении"), а не сам SQL.
    @Query("SELECT * FROM channel_streams WHERE channelId = :channelId ORDER BY priority ASC")
    suspend fun getByChannelId(channelId: String): List<ChannelStreamEntity>

    // ФИКС (аудит): ChannelRepository.toUiItems() раньше вызывал
    // getByChannelId() в цикле — по одному SQL-запросу на КАЖДЫЙ канал.
    // При реальном IPTV-плейлисте на 500-3000+ каналов (обычное дело для
    // M3U) это столько же последовательных обращений к Room. channelId
    // проиндексирован, так что каждый отдельный запрос был быстрым, но
    // сама россыпь запросов давала заметную суммарную задержку. Один
    // батч-запрос вместо N — группировка по channelId делается уже на
    // Kotlin-стороне, в ChannelRepository.
    @Query("SELECT * FROM channel_streams WHERE channelId IN (:channelIds) ORDER BY priority ASC")
    suspend fun getByChannelIds(channelIds: List<String>): List<ChannelStreamEntity>

    @Query("SELECT COUNT(*) FROM channel_streams WHERE channelId = :channelId")
    suspend fun countByChannelId(channelId: String): Int

    @Query("SELECT * FROM channel_streams WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): List<ChannelStreamEntity>

    @Query("DELETE FROM channel_streams WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("DELETE FROM channel_streams WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)

    // ФИКС (аудит): нужен для ChannelRepository.merge() — после переноса
    // стримов слитого канала (reassignChannel) на целевой канал могли
    // остаться две записи на один и тот же физический стрим (если у ОБОИХ
    // каналов уже был стрим от одного источника). Раньше это осознанно
    // оставляли как "не исчезнет само" — теперь чистим сразу.
    @Query("DELETE FROM channel_streams WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // Для UI слияния каналов (подзадача 4): все стримы канала-дубликата
    // переносятся на канал-получатель одним UPDATE, сам канал-дубликат
    // затем удаляется вызывающей стороной (ChannelRepository.merge()).
    @Query("UPDATE channel_streams SET channelId = :newChannelId WHERE channelId = :oldChannelId")
    suspend fun reassignChannel(oldChannelId: String, newChannelId: String)

    @Query("UPDATE channel_streams SET lastCheckedAt = :timestamp, lastCheckStatus = :status, consecutiveFailures = :consecutiveFailures WHERE id = :id")
    suspend fun updateCheckResult(id: String, timestamp: Long, status: String, consecutiveFailures: Int)
}
