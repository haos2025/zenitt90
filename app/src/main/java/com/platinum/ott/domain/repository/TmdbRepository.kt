package com.platinum.ott.domain.repository

import com.platinum.ott.domain.model.TmdbMetadata

interface TmdbRepository {
    suspend fun getMetadata(contentId: String, title: String, year: Int? = null): Result<TmdbMetadata>
}
