package com.platinum.ott.domain.usecase

import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetPlayableUrlUseCase(private val scriptProvider: ScriptProvider, private val authRepo: AuthRepository) {
    suspend fun execute(movieId: String): List<StreamVariant> = withContext(Dispatchers.IO) {
        try {
            val result = scriptProvider.evaluateScript("parser", "getStreams", movieId)
            result?.split(";")?.mapNotNull {
                val parts = it.split("|"); if (parts.size == 2) StreamVariant(parts[0], parts[1]) else null
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
