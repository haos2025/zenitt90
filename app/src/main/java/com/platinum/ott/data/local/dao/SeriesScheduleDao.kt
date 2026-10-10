package com.platinum.ott.data.local.dao

import androidx.room.*
import com.platinum.ott.data.local.entity.SeriesScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: SeriesScheduleEntity)
    @Query("SELECT * FROM series_schedule WHERE nextEpisodeDate > :now ORDER BY nextEpisodeDate ASC")
    fun getUpcoming(now: Long = System.currentTimeMillis()): Flow<List<SeriesScheduleEntity>>
    // ФИКС/НОВОЕ (дизайн Home-полосы): getUpcoming() — строго БУДУЩИЕ даты,
    // на такую серию ещё нельзя нажать "смотреть". Для строки "Новая серия"
    // на Home нужна противоположная выборка — уже вышедшая, но недавно
    // (иначе показывали бы серию, которая вышла полгода назад и её давно
    // посмотрели). Тот же принцип окна, что и в SeriesUpdateWorker
    // (см. его комментарий про 24ч), но чуть шире — полоса на Home не
    // обязана исчезать так же быстро, как push-уведомление.
    @Query("SELECT * FROM series_schedule WHERE nextEpisodeDate <= :now AND nextEpisodeDate > :since ORDER BY nextEpisodeDate DESC LIMIT 1")
    fun getMostRecentlyAired(now: Long = System.currentTimeMillis(), since: Long): Flow<SeriesScheduleEntity?>
    @Query("SELECT * FROM series_schedule WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getBySeriesId(seriesId: String): SeriesScheduleEntity?
}
