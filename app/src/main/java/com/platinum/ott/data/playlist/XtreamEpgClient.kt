package com.platinum.ott.data.playlist

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

// get_short_epg/get_epg — тот же контракт Xtream Codes, что и у
// XtreamVodClient (чужой протокол, не наш формат). Оба действия отдают
// одинаковую обёртку "epg_listings"; get_short_epg — ближайшие N программ
// (параметр limit), get_epg — расписание на сутки вперёд для этого канала.
// title/description ВСЕГДА в base64 по спецификации Xtream (в отличие от
// названия канала в get_live_streams, которое обычным текстом) — decodeOrRaw()
// ниже честно откатывается на исходную строку, если панель это нарушает
// (реальные Xtream-панели, как показал опыт с get_series/get_live_streams в
// XtreamVodClient, не всегда следуют спецификации буквально).
private data class XtreamEpgResponse(
    @SerializedName("epg_listings") val epgListings: List<XtreamEpgListing>? = null
)

private data class XtreamEpgListing(
    val title: String? = null,
    val description: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp") val stopTimestamp: String? = null
)

/**
 * Уже готовая к записи в EpgProgramEntity программа — распаковка base64 и
 * разбор таймстампов сделаны здесь, вызывающий код (PlaylistSourceRepository/
 * будущая подзадача 5) просто маппит 1:1 в Entity, зная channelId, который
 * этот клиент сознательно не знает (см. комментарий у fetchLiveStreams() в
 * XtreamVodClient.kt — те же границы ответственности: клиент только читает
 * панель, ничего не решает про Channel/ChannelStream).
 */
data class XtreamEpgProgram(
    val title: String,
    val description: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

object XtreamEpgClient {
    /**
     * xmltv.php — третий вариант из PROMPT_EPG.md ("Xtream ... xmltv.php"),
     * тот же XMLTV-формат, что и M3U-шный url-tvg, просто у Xtream он не
     * приходит отдельным атрибутом, а строится по тому же принципу, что и
     * ссылки на потоки в XtreamVodClient (host/username/password panel-wide).
     * Разбор самого XMLTV (SAX) — подзадача 3, здесь только адрес.
     */
    fun buildXmltvUrl(host: String, username: String, password: String): String {
        val base = host.trimEnd('/')
        return "$base/xmltv.php?username=$username&password=$password"
    }

    fun getShortEpg(client: OkHttpClient, host: String, username: String, password: String, streamId: String, limit: Int = 4): List<XtreamEpgProgram> =
        fetch(client, host, username, password, "get_short_epg", streamId, limit)

    fun getEpg(client: OkHttpClient, host: String, username: String, password: String, streamId: String): List<XtreamEpgProgram> =
        fetch(client, host, username, password, "get_epg", streamId, limit = null)

    private fun fetch(
        client: OkHttpClient, host: String, username: String, password: String,
        action: String, streamId: String, limit: Int?
    ): List<XtreamEpgProgram> {
        val base = host.trimEnd('/')
        val limitParam = if (limit != null) "&limit=$limit" else ""
        val url = "$base/player_api.php?username=$username&password=$password&action=$action&stream_id=$streamId$limitParam"
        return try {
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
            val response = Gson().fromJson(body, XtreamEpgResponse::class.java)
            (response?.epgListings ?: emptyList()).mapNotNull { it.toProgramOrNull() }
        } catch (_: Exception) {
            // Панель без EPG для этого канала, битый ответ или сетевая
            // ошибка — не должно ронять остальной рефреш, тот же принцип,
            // что и в fetchLiveStreams()/fetchSeriesEpisodes() в XtreamVodClient.
            emptyList()
        }
    }

    private fun XtreamEpgListing.toProgramOrNull(): XtreamEpgProgram? {
        val startSeconds = startTimestamp?.toLongOrNull() ?: return null
        val stopSeconds = stopTimestamp?.toLongOrNull() ?: return null
        val decodedTitle = decodeOrRaw(title) ?: return null
        return XtreamEpgProgram(
            title = decodedTitle,
            description = decodeOrRaw(description),
            startTimeMillis = TimeUnit.SECONDS.toMillis(startSeconds),
            endTimeMillis = TimeUnit.SECONDS.toMillis(stopSeconds)
        )
    }

    private fun decodeOrRaw(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            value
        }
    }
}
