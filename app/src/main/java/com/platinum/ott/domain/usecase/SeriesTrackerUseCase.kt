package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.SeriesScheduleDao
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import com.platinum.ott.domain.ContentIdKind
import com.platinum.ott.domain.repository.TmdbRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class SeriesTrackerUseCase(private val dao: SeriesScheduleDao, private val tmdb: TmdbRepository) {
    fun getUpcoming(): Flow<List<SeriesScheduleEntity>> = dao.getUpcoming()

    // НОВОЕ (Home-полоса вместо HeroBanner): см. комментарий у
    // SeriesScheduleDao.getMostRecentlyAired(). windowMs — за какой срок
    // "новая серия" ещё считается новой на самой полосе (не в push,
    // у push своё, более узкое 24ч окно в SeriesUpdateWorker).
    fun getRecentlyAired(windowMs: Long = TimeUnit.DAYS.toMillis(3)): Flow<SeriesScheduleEntity?> =
        dao.getMostRecentlyAired(since = System.currentTimeMillis() - windowMs)

    // Раньше это был placeholder ("val resp = TmdbApiService::class // placeholder"),
    // ничего не делавший. Возвращает обновлённую запись ТОЛЬКО если дата
    // следующей серии реально изменилась по сравнению с уже сохранённой —
    // так SeriesUpdateWorker знает, стоит ли уведомлять, а не считает
    // каждый периодический опрос "новостью".
    //
    // ФИКС (аудит, ContentIdKind.kt): tmdb.getNextEpisode(seriesName) ищет
    // по ТЕКСТОВОМУ названию — для сериала из собственного плейлиста это
    // могло подтянуть расписание совершенно другого шоу с похожим/
    // совпадающим названием, и уведомление пришло бы с правильным именем
    // (оно берётся из fav.title, не отсюда), но неверными сезоном/серией.
    suspend fun updateSchedule(seriesId: String, seriesName: String): SeriesScheduleEntity? {
        if (!ContentIdKind.isZenithBackendContent(seriesId)) return null
        val next = tmdb.getNextEpisode(seriesName) ?: return null
        val existing = dao.getBySeriesId(seriesId)
        if (existing?.nextEpisodeDate == next.airDateEpochMs) return null
        val entity = SeriesScheduleEntity(seriesId, seriesName, next.airDateEpochMs, next.seasonNum, next.episodeNum, next.episodeName)
        dao.upsert(entity)
        return entity
    }
}
