package com.platinum.ott.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.platinum.ott.data.local.dao.*
import com.platinum.ott.data.local.entity.*

@Database(
    entities = [
        MovieEntity::class, FavoriteEntity::class, FolderEntity::class,
        WatchHistoryEntity::class, MetadataEntity::class, SeriesScheduleEntity::class,
        PluginEntity::class
    ],
    version = 4, exportSchema = true
)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun metadataDao(): MetadataDao
    abstract fun seriesScheduleDao(): SeriesScheduleDao
    abstract fun pluginDao(): PluginDao

    companion object {
        @Volatile private var INSTANCE: ZenithDatabase? = null
        fun getInstance(context: Context): ZenithDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, ZenithDatabase::class.java, "zenith.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
