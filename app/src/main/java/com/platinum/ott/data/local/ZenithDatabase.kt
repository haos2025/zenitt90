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
        PluginEntity::class, PlaylistMovieEntity::class, PlaylistSourceEntity::class
    ],
    version = 13, exportSchema = true
)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun metadataDao(): MetadataDao
    abstract fun seriesScheduleDao(): SeriesScheduleDao
    abstract fun pluginDao(): PluginDao
    abstract fun playlistMovieDao(): PlaylistMovieDao
    abstract fun playlistSourceDao(): PlaylistSourceDao

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

        // Задача "папки в избранном": фильтр ANIME в FavoritesScreen раньше
        // сравнивал contentType с "ANIME" напрямую, хотя contentType уже
        // занят под MOVIE/SERIES и используется для роутинга — независимый
        // столбец, а не переиспользование contentType. NOT NULL DEFAULT 0
        // безопасен для SQLite (все существующие строки получат false).
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `favorites` ADD COLUMN `isAnime` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // История просмотров: группировка серий одного сериала в одну строку
        // (PROMPT_HISTORY_UPGRADE.md) требует знать, какой серии какой сериал
        // принадлежит — nullable ADD COLUMN без DEFAULT, тот же безопасный
        // паттерн, что и в MIGRATION_5_6/6_7/7_8 (существующие строки watch_history
        // просто получат NULL, что и означает "обычный фильм, не серия").
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `seriesId` TEXT")
            }
        }

        // Карусель актёров с фото (PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4) —
        // castJson хранит сериализованный Gson-ом список CastMember, nullable
        // ADD COLUMN без DEFAULT, тот же безопасный паттерн, что и во всех
        // предыдущих миграциях этого файла (старые строки получат NULL,
        // карусель для них появится после следующего фетча по TTL).
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata` ADD COLUMN `castJson` TEXT")
            }
        }

        // Авто-определение аниме при добавлении в избранное (PROMPT_FAVORITES_REDESIGN.md,
        // п.1) требует знать язык оригинала фильма — TMDB отдаёт его как
        // original_language, отдельного поля под это в metadata раньше не
        // было. Nullable ADD COLUMN без DEFAULT — тот же безопасный паттерн,
        // что и во всех предыдущих миграциях этого файла.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata` ADD COLUMN `originalLanguage` TEXT")
            }
        }

        // Задача "Источники" (PROMPT_SOURCES_SCREEN.md) — раньше был один
        // источник, конфиг которого целиком жил в AuthPreferences. Новая
        // таблица playlist_sources хранит список источников (M3U/Xtream)
        // со своим приоритетом/статусом. playlist_movies получает nullable
        // sourceId (тот же безопасный ALTER TABLE-паттерн, что и во всех
        // предыдущих миграциях этого файла) плюс индекс по нему — без
        // индекса запросы "все фильмы источника X" (нужны для
        // refresh(sourceId)/удаления одного источника, следующая подзадача)
        // требовали бы полного скана таблицы при каждом обращении.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_sources` (" +
                        "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`url` TEXT, `host` TEXT, `username` TEXT, `password` TEXT, " +
                        "`enabled` INTEGER NOT NULL, `priority` INTEGER NOT NULL, " +
                        "`lastRefreshedAt` INTEGER, `lastRefreshStatus` TEXT, " +
                        "`legacyIds` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `sourceId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_movies_sourceId` ON `playlist_movies` (`sourceId`)")
            }
        }

        @Volatile private var INSTANCE: ZenithDatabase? = null
        fun getInstance(context: Context): ZenithDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, ZenithDatabase::class.java, "zenith.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration() // остаётся как сетка безопасности для НЕзапланированных скачков версии
                .build().also { INSTANCE = it }
        }
    }
}
