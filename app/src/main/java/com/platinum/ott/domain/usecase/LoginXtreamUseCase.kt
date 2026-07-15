package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.AuthRepository

class LoginXtreamUseCase(private val repo: AuthRepository) {
    suspend fun execute(host: String, user: String, pass: String) = repo.validateAndSaveXtream(host, user, pass)
}
