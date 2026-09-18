package com.platinum.ott.data.playlist

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import okhttp3.OkHttpClient
import okhttp3.Request

private data class XtreamVodItem(
    @SerializedName("stream_id") val streamId: Int = 0,
    val name: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("category_id") val categoryId: String? = null
)

private data class XtreamCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = ""
)

// get_live_streams — раздел живого эфира Xtream-панели, структурно похож
// на get_vod_streams, но с epg_channel_id — это и есть Xtream-эквивалент
// tvg-id из M3U (тот же смысл: ключ для сопоставления канала с EPG/другими
// источниками), просто под другим именем в этом конкретном API.
private data class XtreamLiveItem(
    @SerializedName("stream_id") val streamId: Int = 0,
    val name: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    // PROMPT_EPG.md, подзадача 5 — 1 = панель отдаёт архив ПО ЭТОМУ каналу,
    // tv_archive_duration — на сколько дней назад. Оба поля документированы
    // в Xtream Codes API. ДОПУЩЕНИЕ (честно, не проверено на реальной
    // панели): часть реализаций Xtream отдаёт tv_archive как строку "0"/"1",
    // не число — как это переживёт Gson с полем-Int, не проверялось; если
    // на реальной панели archive определяется неверно, первое, что стоит
    // проверить, — реальный тип этого поля в ответе панели.
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int = 0,
    // PROMPT_EPG.md, подзадача 6 — порядковый номер канала на панели,
    // тот же смысл, что и tvg-chno у M3U. ДОПУЩЕНИЕ (честно, не проверено
    // на реальной панели): часть панелей отдаёт его как "num", часть —
    // как "channel_num"; реализовано только "num", более распространённое
    // в документации Xtream Codes.
    val num: Int? = null
)

/**
 * Результат fetchLiveStreams() — сознательно НЕ PlaylistMovieEntity: канал
 * живого эфира не фильм (нет года/сезона/эпизода), и эта модель — сырые
 * данные с панели для дальнейшего сопоставления в Channel/ChannelStream
 * (см. PROMPT_IPTV_FOUNDATION.md, подзадача "Сопоставление" — она решает,
 * какая из этих записей становится новым Channel, а какая — новым
 * ChannelStream к уже существующему).
 */
data class XtreamLiveStreamInfo(
    val streamId: Int,
    val name: String,
    val logo: String?,
    val categoryName: String?,
    val tvgId: String?,
    val streamUrl: String,
    // PROMPT_EPG.md, подзадача 5 — см. XtreamLiveItem.tvArchive/tvArchiveDuration.
    // 0, если tv_archive != 1, даже когда tvArchiveDuration>0 в ответе —
    // тот же признак "0 = нет катчапа", что и ChannelStreamEntity.catchupDays.
    val catchupDays: Int,
    // PROMPT_EPG.md, подзадача 6 — см. XtreamLiveItem.num.
    val channelNumber: Int?
)

// get_series — список сериалов (без эпизодов, только карточка сериала).
private data class XtreamSeriesItem(
    @SerializedName("series_id") val seriesId: Int = 0,
    val name: String = "",
    val cover: String? = null,
    @SerializedName("category_id") val categoryId: String? = null
)

// get_series_info?series_id=N — episodes сгруппированы ПО НОМЕРУ СЕЗОНА
// как ключу мапы (строка "1", "2"...), это контракт самого Xtream API,
// не наше решение.
private data class XtreamSeriesInfoResponse(
    val episodes: Map<String, List<XtreamEpisodeItem>>? = null
)
private data class XtreamEpisodeItem(
    val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    val title: String? = null,
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    val season: Int = 0
)

/**
 * Xtream Codes VOD + Series API — стандартный, широко используемый протокол
 * панелей IPTV-провайдеров (чужой контракт, не наш формат, менять нельзя).
 *
 * Раньше здесь был ТОЛЬКО get_vod_streams (фильмы) — раздел "Сериалы"
 * Xtream (get_series/get_series_info) не читался вообще, у Movie/
 * PlaylistMovieEntity физически не было полей seasonNumber/episodeNumber,
 * чтобы это куда-то положить.
 *
 * ВАЖНОЕ ОГРАНИЧЕНИЕ (честно, не молчком): get_series_info вызывается
 * ОТДЕЛЬНО на каждый сериал (N+1 запросов, это ограничение самого Xtream
 * API — он не отдаёт эпизоды всех сериалов одним ответом). На панели с
 * сотнями сериалов первый refresh() после смены источника может занять
 * заметное время. TTL кэша (час, см. PlaylistRepository) означает, что это
 * происходит не на каждый заход в приложение, а раз в час максимум.
 */
object XtreamVodClient {
    fun fetch(client: OkHttpClient, host: String, username: String, password: String): List<PlaylistMovieEntity> {
        val base = host.trimEnd('/')
        val gson = Gson()

        val categories = try {
            val req = Request.Builder()
                .url("$base/player_api.php?username=$username&password=$password&action=get_vod_categories")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                gson.fromJson(body, Array<XtreamCategory>::class.java)
            }.associate { it.categoryId to it.categoryName }
        } catch (_: Exception) {
            emptyMap()
        }

        val yearRegex = Regex("\\((\\d{4})\\)")

        val movies = try {
            val req = Request.Builder()
                .url("$base/player_api.php?username=$username&password=$password&action=get_vod_streams")
                .build()
            val items = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                gson.fromJson(body, Array<XtreamVodItem>::class.java) ?: emptyArray()
            }
            items.map { item ->
                val year = yearRegex.find(item.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                PlaylistMovieEntity(
                    id = "xt_${item.streamId}",
                    title = item.name,
                    year = year,
                    poster = item.streamIcon,
                    genre = categories[item.categoryId] ?: "Мой плейлист",
                    streamUrl = "$base/movie/$username/$password/${item.streamId}.${item.containerExtension}"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        val episodes = fetchSeriesEpisodes(client, gson, base, username, password, categories)

        return movies + episodes
    }

    /**
     * get_live_categories + get_live_streams — раздел живого эфира.
     * Не вызывается из fetch() и не подмешивается в его результат: живой
     * эфир — это Channel/ChannelStream (см. PROMPT_IPTV_FOUNDATION.md),
     * отдельный слой от PlaylistMovieEntity, вызывающий код сам решает,
     * когда и куда класть результат — эта функция только достаёт данные
     * с панели, ничего не пишет и не сопоставляет.
     *
     * ДОПУЩЕНИЕ (не проверено на реальной панели, честно): расширение
     * потока для live-ссылок Xtream в get_live_streams обычно не
     * приходит отдельным полем (в отличие от VOD, где container_extension
     * есть у каждого item) — стандартная договорённость самого протокола
     * -- ".ts" для прямого потока. Часть панелей отдаёт и m3u8 по той же
     * ссылке с иным расширением — если на реальном плейлисте окажется не
     * так, поправить константу LIVE_EXTENSION ниже, это не архитектурное
     * решение, а один параметр.
     */
    private const val LIVE_EXTENSION = "ts"

    fun fetchLiveStreams(client: OkHttpClient, host: String, username: String, password: String): List<XtreamLiveStreamInfo> {
        val base = host.trimEnd('/')
        val gson = Gson()

        val categories = try {
            val req = Request.Builder()
                .url("$base/player_api.php?username=$username&password=$password&action=get_live_categories")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                gson.fromJson(body, Array<XtreamCategory>::class.java)
            }.associate { it.categoryId to it.categoryName }
        } catch (_: Exception) {
            emptyMap()
        }

        return try {
            val req = Request.Builder()
                .url("$base/player_api.php?username=$username&password=$password&action=get_live_streams")
                .build()
            val items = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                gson.fromJson(body, Array<XtreamLiveItem>::class.java) ?: emptyArray()
            }
            items.map { item ->
                XtreamLiveStreamInfo(
                    streamId = item.streamId,
                    name = item.name,
                    logo = item.streamIcon,
                    categoryName = categories[item.categoryId],
                    tvgId = item.epgChannelId?.ifBlank { null },
                    streamUrl = "$base/live/$username/$password/${item.streamId}.$LIVE_EXTENSION",
                    catchupDays = if (item.tvArchive == 1) item.tvArchiveDuration else 0,
                    channelNumber = item.num
                )
            }
        } catch (_: Exception) {
            emptyList() // панель без раздела живого эфира — не ошибка, как и с get_series
        }
    }

    private fun fetchSeriesEpisodes(
        client: OkHttpClient, gson: Gson, base: String, username: String, password: String,
        categories: Map<String, String>
    ): List<PlaylistMovieEntity> {
        val seriesList = try {
            val req = Request.Builder()
                .url("$base/player_api.php?username=$username&password=$password&action=get_series")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                gson.fromJson(body, Array<XtreamSeriesItem>::class.java) ?: emptyArray()
            }
        } catch (_: Exception) {
            return emptyList() // панель без раздела "Сериалы" — не ошибка, просто нечего добавить
        }

        val result = mutableListOf<PlaylistMovieEntity>()
        for (series in seriesList) {
            try {
                val infoReq = Request.Builder()
                    .url("$base/player_api.php?username=$username&password=$password&action=get_series_info&series_id=${series.seriesId}")
                    .build()
                val info = client.newCall(infoReq).execute().use { resp ->
                    val body = resp.body?.string() ?: "{}"
                    gson.fromJson(body, XtreamSeriesInfoResponse::class.java)
                }
                val seriesGenre = categories[series.categoryId] ?: "Сериалы"
                val seriesKey = "xt_series_${series.seriesId}"
                info?.episodes?.forEach { (_, episodesInSeason) ->
                    episodesInSeason.forEach { ep ->
                        val epTitle = if (!ep.title.isNullOrBlank() && ep.title != "Episode ${ep.episodeNum}")
                            "${series.name} S${ep.season}E${ep.episodeNum} — ${ep.title}"
                        else
                            "${series.name} S${ep.season}E${ep.episodeNum}"
                        result += PlaylistMovieEntity(
                            id = "xt_series_${series.seriesId}_${ep.id}",
                            title = epTitle,
                            year = 0,
                            poster = series.cover,
                            genre = seriesGenre,
                            streamUrl = "$base/series/$username/$password/${ep.id}.${ep.containerExtension}",
                            seriesId = seriesKey,
                            seriesTitle = series.name,
                            seasonNumber = ep.season,
                            episodeNumber = ep.episodeNum
                        )
                    }
                }
            } catch (_: Exception) {
                // Один сбойный сериал (например, панель отдала пустой/битый
                // ответ на конкретный series_id) не должен ронять весь
                // остальной список — пропускаем и идём дальше.
            }
        }
        return result
    }
}
