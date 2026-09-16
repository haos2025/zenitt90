package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.ChannelDao
import com.platinum.ott.data.local.dao.ChannelStreamDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Читающая/управляющая сторона Channel/ChannelStream — отдельно от
 * ChannelMatchingRepository (тот только пишет из PlaylistSourceRepository.refresh(),
 * не знает про UI). Тот же принцип разделения, что уже есть в проекте между
 * PlaylistSourceRepository (CRUD источников) и PlaylistRepository (агрегация
 * для остального приложения) — разные ответственности, разные файлы.
 */
data class ChannelUiItem(
    val id: String,
    val canonicalName: String,
    val logo: String?,
    val category: String?,
    val regionHint: String?,
    val isSubscribed: Boolean,
    // Есть tvg-id → канал уже надёжно сопоставлен между источниками
    // автоматически, слияние ему не нужно. Нет tvg-id → кандидат на ручное
    // слияние (см. PROMPT_IPTV_FOUNDATION.md, "спрашивать человека, не гадать").
    val isMergeCandidate: Boolean,
    val streamCount: Int,
    // Агрегат по всем ChannelStreamEntity этого канала — "alive", если хотя
    // бы один источник живой (ровно то, что и нужно для воспроизведения
    // при fallback, следующая подзадача); "dead" — все проверенные мертвы;
    // "unknown" — ни разу не проверялись (health-check ещё не добрался,
    // или нет ни одного стрима вообще, см. streamCount == 0).
    val healthStatus: String
)

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao
) {
    suspend fun getAll(): List<ChannelUiItem> = withContext(Dispatchers.IO) {
        channelDao.getAll().map { channel ->
            val streams = channelStreamDao.getByChannelId(channel.id)
            ChannelUiItem(
                id = channel.id,
                canonicalName = channel.canonicalName,
                logo = channel.logo,
                category = channel.category,
                regionHint = channel.regionHint,
                isSubscribed = channel.isSubscribed,
                isMergeCandidate = channel.tvgId == null,
                streamCount = streams.size,
                healthStatus = when {
                    streams.any { it.lastCheckStatus == "alive" } -> "alive"
                    streams.isNotEmpty() && streams.all { it.lastCheckStatus == "dead" } -> "dead"
                    else -> "unknown"
                }
            )
        }
    }

    suspend fun getSubscribedCount(): Int = withContext(Dispatchers.IO) { channelDao.getSubscribed().size }

    suspend fun setSubscribed(channelId: String, isSubscribed: Boolean) = withContext(Dispatchers.IO) {
        channelDao.setSubscribed(channelId, isSubscribed)
    }

    suspend fun rename(channelId: String, name: String, regionHint: String?) = withContext(Dispatchers.IO) {
        channelDao.rename(channelId, name, regionHint)
    }

    /**
     * Канал без живых стримов (все источники, что его отдавали, удалены/
     * перестали его показывать) — не то же самое, что "неживой стрим"
     * (lastCheckStatus == "dead", тот остаётся, просто не выбирается при
     * воспроизведении, следующая подзадача). Здесь именно 0 записей
     * ChannelStreamEntity вообще, оставлять такую пустую запись Channel в
     * списке смысла нет.
     */
    suspend fun delete(channelId: String) = withContext(Dispatchers.IO) {
        channelStreamDao.deleteByChannelId(channelId)
        channelDao.deleteById(channelId)
    }

    /**
     * Слияние вручную подтверждённое пользователем (см. UI слияния) — не
     * автоматическая эвристика. targetChannelId остаётся (сохраняет свои
     * canonicalName/isSubscribed/regionHint как есть), sourceChannelId
     * теряет все свои ChannelStreamEntity (переносятся на target) и
     * удаляется целиком. Если у обоих каналов был стрим от одного и того
     * же источника (streamId детерминирован как "cs_<sourceId>_<channelId>",
     * см. ChannelMatchingRepository) — после переноса у target окажется
     * два разных id, указывающих на тот же (sourceId, физический стрим) —
     * это не коллизия PRIMARY KEY (разные строковые id), но и не
     * дедуплицируется автоматически; следующий refresh() того источника
     * пересоздаст стрим с корректным id под target и лишняя запись
     * перестанет обновляться, но сама не исчезнет — доп. чистка не в этой
     * подзадаче, отмечено как известное ограничение.
     */
    suspend fun merge(sourceChannelId: String, targetChannelId: String) = withContext(Dispatchers.IO) {
        if (sourceChannelId == targetChannelId) return@withContext
        channelStreamDao.reassignChannel(sourceChannelId, targetChannelId)
        channelDao.deleteById(sourceChannelId)
    }
}
