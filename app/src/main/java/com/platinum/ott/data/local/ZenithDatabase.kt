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
    version = 8, exportSchema = true
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

        // Раньше #EXTVLCOPT:http-user-agent=.../http-referrer=... из M3U
        // полностью терялся при парсинге — оба столбца NULLABLE, ADD COLUMN
        // без DEFAULT в SQLite для nullable-столбца безопасен, существующие
        // строки playlist_movies просто получат NULL (перекачаются заново
        // при следующем refresh() всё равно, TTL 1 час).
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `userAgent` TEXT")
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `referrer` TEXT")
            }
        }

        // Добавляем поддержку сериалов Xtream (get_series_info) — раньше
        // XtreamVodClient вообще не знал о сериалах, только плоский VOD.
        // Все три столбца nullable, без DEFAULT — как и в MIGRATION_5_6,
        // это безопасно для SQLite, а playlist_movies всё равно
        // перекачивается заново при следующем refresh() (TTL 1 час).
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `seriesId` TEXT")
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `seasonNumber` INTEGER")
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `episodeNumber` INTEGER")
            }
        }

        // Экран "по сериалам" — нужно чистое отображаемое название сериала
        // отдельно от названия конкретного эпизода ("Шоу S01E02 — ...").
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `seriesTitle` TEXT")
            }
        }

        @Volatile private var INSTANCE: ZenithDatabase? = null
        fun getInstance(context: Context): ZenithDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, ZenithDatabase::class.java, "zenith.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration() // остаётся как сетка безопасности для НЕзапланированных скачков версии
                .build().also { INSTANCE = it }
        }
    }
}
