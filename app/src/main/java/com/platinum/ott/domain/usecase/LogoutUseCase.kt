package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.AuthRepository

class LogoutUseCase(private val repo: AuthRepository) {
    fun execute() = repo.logout()
}
