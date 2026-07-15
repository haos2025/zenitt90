package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "series_schedule")
data class SeriesScheduleEntity(
    @PrimaryKey val seriesId: String, val seriesName: String = "",
    val nextEpisodeDate: Long = 0, val seasonNum: Int = 0,
    val episodeNum: Int = 0, val episodeName: String = ""
)
