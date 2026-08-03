package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.SeriesScheduleDao
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import com.platinum.ott.domain.repository.TmdbRepository
import kotlinx.coroutines.flow.Flow

class SeriesTrackerUseCase(private val dao: SeriesScheduleDao, private val tmdb: TmdbRepository) {
    fun getUpcoming(): Flow<List<SeriesScheduleEntity>> = dao.getUpcoming()

    // Раньше это был placeholder ("val resp = TmdbApiService::class // placeholder"),
    // ничего не делавший. Возвращает обновлённую запись ТОЛЬКО если дата
    // следующей серии реально изменилась по сравнению с уже сохранённой —
    // так SeriesUpdateWorker знает, стоит ли уведомлять, а не считает
    // каждый периодический опрос "новостью".
    suspend fun updateSchedule(seriesId: String, seriesName: String): SeriesScheduleEntity? {
        val next = tmdb.getNextEpisode(seriesName) ?: return null
        val existing = dao.getBySeriesId(seriesId)
        if (existing?.nextEpisodeDate == next.airDateEpochMs) return null
        val entity = SeriesScheduleEntity(seriesId, seriesName, next.airDateEpochMs, next.seasonNum, next.episodeNum, next.episodeName)
        dao.upsert(entity)
        return entity
    }
}
