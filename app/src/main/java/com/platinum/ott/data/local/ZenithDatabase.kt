package com.platinum.ott.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.platinum.ott.data.local.dao.*
import com.platinum.ott.data.local.entity.*

@Database(
    entities = [
        MovieEntity::class, FavoriteEntity::class, FolderEntity::class,
        WatchHistoryEntity::class, MetadataEntity::class, SeriesScheduleEntity::class,
        PluginEntity::class, PlaylistMovieEntity::class
    ],
    version = 5, exportSchema = true
)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun metadataDao(): MetadataDao
    abstract fun seriesScheduleDao(): SeriesScheduleDao
    abstract fun pluginDao(): PluginDao
    abstract fun playlistMovieDao(): PlaylistMovieDao

    companion object {
        // Раньше версия схемы никогда не поднималась после первого релиза,
        // поэтому fallbackToDestructiveMigration() не успевал проявить себя
        // как проблема. Сейчас в базе уже есть реальные favorites/watch_history
        // (только начали проверять) — destructive-фолбэк стёр бы их целиком
        // при первом же обновлении с версии 4 на 5. Явная миграция создаёт
        // только новую таблицу, ничего существующего не трогает.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_movies` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `year` INTEGER NOT NULL, " +
                        "`poster` TEXT, `genre` TEXT, `streamUrl` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile private var INSTANCE: ZenithDatabase? = null
        fun getInstance(context: Context): ZenithDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, ZenithDatabase::class.java, "zenith.db")
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration() // остаётся как сетка безопасности для НЕзапланированных скачков версии
                .build().also { INSTANCE = it }
        }
    }
}
