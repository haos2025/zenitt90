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
        PluginEntity::class, PlaylistMovieEntity::class, PlaylistSourceEntity::class,
        ChannelEntity::class, ChannelStreamEntity::class, EpgProgramEntity::class
    ],
    version = 20, exportSchema = true
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
    abstract fun channelDao(): ChannelDao
    abstract fun channelStreamDao(): ChannelStreamDao
    abstract fun epgProgramDao(): EpgProgramDao

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

        // IPTV-фундамент (PROMPT_IPTV_FOUNDATION.md) — раньше живой канал
        // от разных источников представлялся отдельными несвязанными
        // строками playlist_movies (риск дублей в избранном, нет способа
        // переключиться на другой источник при отказе одного без потери
        // избранного/истории). Новые таблицы разделяют понятия: channels —
        // канонический канал (то, на что ссылается избранное), channel_streams —
        // конкретная ссылка конкретного источника на него.
        //
        // Существующие данные НЕ переносятся этой миграцией: до сих пор
        // playlist_movies не различал "живой канал" и "обычный VOD" никаким
        // отдельным флагом, поэтому надёжно понять, какие из уже сохранённых
        // строк на самом деле каналы, а какие — фильмы, здесь неоткуда — это
        // и есть работа подзадачи "Сопоставление" (следующая), которая при
        // очередном refresh() источника сама создаст Channel/ChannelStream
        // из свежих данных, используя tvgId. Явный столбец tvgId в
        // playlist_movies (добавлен в прошлой подзадаче на уровне Entity/
        // парсера) в схеме здесь появляется впервые — ALTER TABLE без
        // DEFAULT, тот же безопасный nullable-паттерн, что и во всех
        // предыдущих миграциях этого файла.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `tvgId` TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channels` (" +
                        "`id` TEXT NOT NULL, `canonicalName` TEXT NOT NULL, `tvgId` TEXT, " +
                        "`logo` TEXT, `category` TEXT, `regionHint` TEXT, " +
                        "`isSubscribed` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_streams` (" +
                        "`id` TEXT NOT NULL, `channelId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, `rawTitle` TEXT, `userAgent` TEXT, " +
                        "`referrer` TEXT, `lastCheckedAt` INTEGER, `lastCheckStatus` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_streams_channelId` ON `channel_streams` (`channelId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_streams_sourceId` ON `channel_streams` (`sourceId`)")
            }
        }

        // Явный переключатель "это плейлист живых каналов" на добавлении
        // M3U-источника (продуктовое решение сессии "Сопоставление" —
        // tvg-id один не разделяет VOD/live внутри плоского M3U-списка,
        // нужен явный выбор пользователя). NOT NULL DEFAULT 'vod' безопасен
        // для SQLite и не меняет поведение уже существующих источников.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_sources` ADD COLUMN `contentKind` TEXT NOT NULL DEFAULT 'vod'")
            }
        }

        // Health-check (PROMPT_IPTV_FOUNDATION.md) — бэкофф-счётчик для
        // экспоненциального интервала повторной проверки, см. комментарий
        // у поля в ChannelStreamEntity. NOT NULL DEFAULT 0 безопасен для
        // SQLite, существующие записи (ещё ни разу не проверялись) получают
        // 0 — то же значение, с которым живёт свежесозданная запись.
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channel_streams` ADD COLUMN `consecutiveFailures` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Данные EPG (PROMPT_EPG.md, подзадача 1) — новая таблица под
        // программы передач, ни одна существующая таблица не меняется.
        // Составной индекс сразу по (channelId, startTimeMillis) — именно
        // так строится и запрос сетки (getForChannelInRange), и запрос
        // "текущая программа" (getCurrentForChannel), без индекса оба
        // требовали бы полного скана таблицы на каждое обращение, как и
        // объяснялось для index_playlist_movies_sourceId в MIGRATION_12_13.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `epg_programs` (" +
                        "`id` TEXT NOT NULL, `channelId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`description` TEXT, `category` TEXT, " +
                        "`startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_epg_programs_channelId_startTimeMillis` " +
                        "ON `epg_programs` (`channelId`, `startTimeMillis`)"
                )
            }
        }

        // PROMPT_EPG.md, подзадача 2 ("Источники EPG") — два независимых
        // дополнения, оба ADD COLUMN без DEFAULT (тот же безопасный
        // nullable-паттерн, что и во всех предыдущих миграциях этого файла):
        // epgUrl на playlist_sources — адрес XMLTV из `url-tvg=` M3U-плейлиста
        // (только для type == "m3u", см. комментарий у поля); externalStreamId
        // на channel_streams — числовой Xtream stream_id ЭТОГО источника,
        // нужен для get_short_epg/get_epg (см. комментарий у поля,
        // XtreamEpgClient.kt). Разные таблицы, но один логический шаг —
        // "откуда брать EPG для канала", поэтому одна миграция на двоих.
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_sources` ADD COLUMN `epgUrl` TEXT")
                db.execSQL("ALTER TABLE `channel_streams` ADD COLUMN `externalStreamId` TEXT")
            }
        }

        // PROMPT_EPG.md, подзадача 5 (timeshift/catch-up) — тот же принцип
        // "одна миграция на двоих", что и MIGRATION_17_18: catchupDays/
        // catchupTemplate на channel_streams (см. комментарий у полей) и
        // те же два столбца на playlist_movies (сырые catchup-days/
        // catchup-source из M3U ДО того, как строка станет
        // ChannelStreamEntity через ChannelMatchingRepository). catchupDays
        // на channel_streams — NOT NULL DEFAULT 0 (0 уже значит "нет
        // архива", безопасное значение по умолчанию для существующих
        // строк); на playlist_movies — nullable без DEFAULT, тот же паттерн,
        // что и у tvgId там же (существующие строки перекачаются заново
        // при следующем refresh()).
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channel_streams` ADD COLUMN `catchupDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel_streams` ADD COLUMN `catchupTemplate` TEXT")
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `catchupDays` INTEGER")
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `catchupTemplate` TEXT")
            }
        }

        // PROMPT_EPG.md, подзадача 6 (заппинг по номерам) — только
        // playlist_movies: ChannelEntity.sortOrder уже существует в схеме
        // (переиспользуется как номер канала, см. комментарий у поля),
        // новый столбец нужен только на "сырых" M3U-записях ДО того, как
        // строка станет ChannelStreamEntity через ChannelMatchingRepository
        // (тот же паттерн, что и catchupDays/catchupTemplate там же).
        // Nullable без DEFAULT — тот же безопасный паттерн, что и у tvgId.
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_movies` ADD COLUMN `channelNumber` INTEGER")
            }
        }

        @Volatile private var INSTANCE: ZenithDatabase? = null
        fun getInstance(context: Context): ZenithDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, ZenithDatabase::class.java, "zenith.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .fallbackToDestructiveMigration() // остаётся как сетка безопасности для НЕзапланированных скачков версии
                .build().also { INSTANCE = it }
        }
    }
}
