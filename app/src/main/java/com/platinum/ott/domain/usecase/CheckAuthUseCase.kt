package com.platinum.ott.domain.usecase

import com.platinum.ott.domain.repository.AuthRepository

class CheckAuthUseCase(private val repo: AuthRepository) {
    fun execute() = repo.isLoggedIn()
}
