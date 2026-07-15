package com.platinum.ott.domain.repository

interface AuthRepository {
    fun isLoggedIn(): Boolean
    fun getAuthType(): String?
    suspend fun validateAndSaveM3U(url: String): Result<Unit>
    suspend fun validateAndSaveXtream(host: String, username: String, password: String): Result<Unit>
    fun logout()
}
