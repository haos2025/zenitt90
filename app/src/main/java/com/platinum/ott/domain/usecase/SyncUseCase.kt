package com.platinum.ott.domain.usecase

import com.platinum.ott.sync.SyncRepository

class SyncUseCase(private val repo: SyncRepository) {
    suspend fun syncNow() = repo.sync()
}
