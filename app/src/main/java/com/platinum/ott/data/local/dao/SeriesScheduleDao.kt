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
    @Query("SELECT * FROM series_schedule WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getBySeriesId(seriesId: String): SeriesScheduleEntity?
}
