package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.ChannelEntity

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isSubscribed = 1 ORDER BY sortOrder ASC")
    suspend fun getSubscribed(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    // Для автосопоставления по tvg-id при обработке нового/обновлённого
    // источника — см. подзадачу "Сопоставление".
    @Query("SELECT * FROM channels WHERE tvgId = :tvgId LIMIT 1")
    suspend fun getByTvgId(tvgId: String): ChannelEntity?

    // PROMPT_EPG.md, подзадача 6 (заппинг по номерам) — только среди
    // подписанных: набирая номер на пульте, пользователь дозванивается до
    // канала из СВОЕГО списка, не до любого канала каталога панели.
    @Query("SELECT * FROM channels WHERE sortOrder = :number AND isSubscribed = 1 LIMIT 1")
    suspend fun getSubscribedByNumber(number: Int): ChannelEntity?

    // Для авто-нумерации новых каналов без явного tvg-chno/num от источника
    // (см. ChannelMatchingRepository.resolveChannelId()) — следующий
    // свободный номер, а не 0/повтор уже занятого.
    @Query("SELECT MAX(sortOrder) FROM channels")
    suspend fun getMaxSortOrder(): Int?

    @Query("UPDATE channels SET isSubscribed = :isSubscribed WHERE id = :id")
    suspend fun setSubscribed(id: String, isSubscribed: Boolean)

    @Query("UPDATE channels SET canonicalName = :name, regionHint = :regionHint WHERE id = :id")
    suspend fun rename(id: String, name: String, regionHint: String?)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: String)
}
