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

/**
 * Xtream Codes VOD API — стандартный, широко используемый протокол панелей
 * IPTV-провайдеров (чужой контракт, не наш формат, менять нельзя).
 * get_vod_categories подтягивается один раз перед get_vod_streams только
 * чтобы превратить category_id в человекочитаемое название для группировки
 * по рядам на HomeScreen — тот же смысл, что group-title у M3U.
 *
 * Блокирующий, не suspend — как и validateAndSaveXtream в AuthRepositoryImpl,
 * вызывающая сторона (PlaylistRepository) сама оборачивает в
 * withContext(Dispatchers.IO).
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

        val req = Request.Builder()
            .url("$base/player_api.php?username=$username&password=$password&action=get_vod_streams")
            .build()
        val items = client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: "[]"
            gson.fromJson(body, Array<XtreamVodItem>::class.java) ?: emptyArray()
        }

        val yearRegex = Regex("\\((\\d{4})\\)")
        return items.map { item ->
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
    }
}
