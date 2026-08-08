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
