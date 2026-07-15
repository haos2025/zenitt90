package com.platinum.ott.sync

interface SyncRepository {
    suspend fun sync(): Result<Unit>
}
