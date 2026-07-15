package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.SeriesScheduleDao
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import com.platinum.ott.domain.repository.TmdbRepository
import kotlinx.coroutines.flow.Flow

class SeriesTrackerUseCase(private val dao: SeriesScheduleDao, private val tmdb: TmdbRepository) {
    fun getUpcoming(): Flow<List<SeriesScheduleEntity>> = dao.getUpcoming()
    suspend fun updateSchedule(seriesId: String, seriesName: String) {
        try {
            val id = seriesId.toIntOrNull() ?: return
            val resp = com.platinum.ott.data.remote.tmdb.TmdbApiService::class // placeholder
            // In real impl, call tmdb api for next_episode_to_air
        } catch (_: Exception) {}
    }
}
