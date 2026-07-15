package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.MovieDao

class ClearCacheUseCase(private val dao: MovieDao) {
    suspend fun execute() = dao.clearAll()
}
