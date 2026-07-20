package com.platinum.ott.sync

interface SyncRepository {
    suspend fun sync(): Result<Unit>
    suspend fun createPairingCode(): Result<PairingCode>
    suspend fun redeemPairingCode(code: String): Result<Unit>
}

data class PairingCode(val code: String, val expiresInSeconds: Int)
