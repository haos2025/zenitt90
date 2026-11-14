package com.platinum.ott.data.local.entity

import androidx.room.Entity

/**
 * Метка удаления для синхронизации v2: чтобы удаление избранного/истории на одном
 * устройстве дошло до остальных, его нужно запомнить (иначе запись просто исчезает
 * и сервер никогда не узнает). Строки пишут SQL-триггеры (см. ZenithDatabase.SYNC_TRIGGERS_SQL):
 * любое DELETE из favorites / watch_history — в том числе массовое и из любого места
 * приложения — оставляет здесь метку, а любой INSERT той же записи метку снимает.
 * kind: "favorite" | "history".
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "contentId"])
data class SyncTombstoneEntity(
    val kind: String,
    val contentId: String,
    val deletedAt: Long
)
