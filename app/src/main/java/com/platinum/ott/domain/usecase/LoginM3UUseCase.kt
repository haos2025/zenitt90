package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.AuthRepository

class LoginM3UUseCase(private val repo: AuthRepository) {
    suspend fun execute(url: String) = repo.validateAndSaveM3U(url)
}
