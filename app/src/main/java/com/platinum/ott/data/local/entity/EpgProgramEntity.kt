package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PROMPT_EPG.md, подзадача 1 ("Модель данных") — программа передач для
 * одного канала на один временной слот.
 *
 * Привязана к `channelId` (ChannelEntity.id, канонический канал), а не к
 * конкретному ChannelStreamEntity/источнику: избранное/UI уже работают на
 * уровне канонического канала (см. ChannelEntity.kt), и сетка программ
 * должна показывать одну программу для канала независимо от того, сколько
 * источников сейчас его отдают. Если два источника пришлют разные версии
 * расписания для одного канала — как источники EPG выбираются/приоритизируются
 * между собой, решает подзадача 3 (парсеры XMLTV/Xtream), здесь это вне
 * скоупа: эта сущность только хранит уже готовые (кем-то выбранные) программы.
 *
 * `id` — не автогенерируемый (не UUID), а составной "channelId:startTimeMillis":
 * XMLTV/Xtream отдают расписание целиком при каждом фетче, а не отдельными
 * дельтами, поэтому upsert по такому составному ключу (OnConflictStrategy.REPLACE
 * в EpgProgramDao) естественно обновляет уже известный слот при повторном фетче
 * того же канала, вместо накопления дублей. Один канал не может иметь двух
 * разных программ с одинаковым временем начала, так что коллизий по смыслу
 * возникнуть не должно.
 *
 * Скользящее окно (−2ч/+48ч, см. PROMPT_EPG.md) не хранится как отдельное
 * состояние здесь — это политика чтения/очистки (что именно фетчат парсеры и
 * когда запускается очистка), а не свойство самой строки. Фоновая чистка
 * устаревших программ — EpgCleanupWorker.kt, по аналогии с SeriesUpdateWorker.
 */
@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["channelId", "startTimeMillis"])]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    // XMLTV/Xtream отдают жанр как произвольную строку категории, не
    // фиксированный enum — тот же подход, что и Movie.genre в этом проекте.
    val category: String? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)
