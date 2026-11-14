package com.platinum.ott.sync

interface SyncRepository {
    /** Полная синхронизация с сервером (v2). trigger: "manual" | "auto" | "pairing". */
    suspend fun sync(trigger: String = "manual"): Result<SyncReport>
    suspend fun createPairingCode(): Result<PairingCode>
    suspend fun redeemPairingCode(code: String): Result<RedeemResult>
    suspend fun devices(): Result<List<DeviceInfo>>
    suspend fun renameDevice(id: String, name: String): Result<Unit>
    suspend fun unlinkDevice(id: String): Result<Unit>
    /** Последние отчёты, новые первыми. */
    fun recentReports(): List<SyncReport>
}

data class PairingCode(val code: String, val expiresInSeconds: Int)
