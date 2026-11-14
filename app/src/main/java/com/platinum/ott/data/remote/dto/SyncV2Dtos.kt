package com.platinum.ott.data.remote.dto

import com.google.gson.JsonObject

/**
 * DTO протокола синхронизации v2 (`/v2/*` на zenith-backend, см. app/routers/sync_v2.py).
 * Все поля имеют значения по умолчанию — Gson тогда использует безаргументный
 * конструктор и не оставляет null в не-null полях, если сервер чего-то не прислал.
 */
data class DeviceRegisterRequest(val name: String, val type: String, val legacyDeviceId: String? = null)
data class DeviceRegisterResponse(
    val deviceId: String = "", val token: String = "", val cursor: Long = 0,
    val imported: Map<String, Int> = emptyMap()
)
data class DeviceRenameRequest(val name: String)
data class AckInfoDto(
    val cursor: Long = 0, val at: Long = 0, val applied: Map<String, Int> = emptyMap(),
    val checksum: String = "", val matchesServer: Boolean = false
)
data class DeviceInfoDto(
    val id: String = "", val name: String = "", val type: String = "other",
    val createdAt: Long = 0, val lastSeenAt: Long = 0, val lastSyncAt: Long = 0,
    val lastAck: AckInfoDto? = null, val isCurrent: Boolean = false
)
data class DevicesResponse(val devices: List<DeviceInfoDto> = emptyList())

/** Запись для отправки: kind = "favorite" | "history". */
data class SyncPushItemDto(
    val kind: String, val contentId: String, val updatedAt: Long,
    val deleted: Boolean = false, val data: JsonObject = JsonObject()
)
data class SyncPushRequestV2(val items: List<SyncPushItemDto> = emptyList())
data class SyncPushResponseV2(
    val accepted: Int = 0, val updated: Int = 0, val ignored: Int = 0, val rejected: Int = 0,
    val errors: List<String> = emptyList(), val cursor: Long = 0, val serverTime: Long = 0
)

/** Запись из выдачи сервера; seq — серверный номер изменения, by — id устройства-автора. */
data class SyncItemDto(
    val kind: String = "", val contentId: String = "", val updatedAt: Long = 0,
    val deleted: Boolean = false, val data: JsonObject = JsonObject(),
    val seq: Long = 0, val by: String = ""
)
data class SyncPullResponseV2(
    val items: List<SyncItemDto> = emptyList(), val cursor: Long = 0,
    val hasMore: Boolean = false, val resetRequired: Boolean = false, val serverTime: Long = 0
)
data class SyncAckRequest(val cursor: Long, val applied: Map<String, Int>, val checksum: String)
data class SyncAckResponse(val status: String = "", val matchesServer: Boolean = false, val serverChecksum: String = "")

data class PairingCreateV2Dto(val code: String = "", val expiresInSeconds: Int = 0)
data class PairingRedeemV2Request(val code: String, val name: String? = null, val type: String = "other")
data class PairingRedeemV2Dto(
    val deviceId: String = "", val token: String? = null, val cursor: Long = 0,
    val resetRequired: Boolean = true, val merged: Map<String, Int> = emptyMap(),
    val inviterName: String = ""
)
