package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.ChannelDao
import com.platinum.ott.data.local.dao.ChannelStreamDao
import com.platinum.ott.data.local.entity.ChannelEntity
import com.platinum.ott.data.local.entity.ChannelStreamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Единая сырая форма канала независимо от протокола-источника (M3U-запись
 * с tvg-id или Xtream XtreamLiveStreamInfo с epg_channel_id) — сам
 * ChannelMatchingRepository не знает и не должен знать, откуда пришли эти
 * данные, только как их свести в Channel/ChannelStream.
 */
data class RawChannelCandidate(
    val tvgId: String?,
    val name: String,
    val logo: String?,
    val category: String?,
    val streamUrl: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    // PROMPT_EPG.md, подзадача 2 — числовой Xtream stream_id ЭТОГО
    // источника, если кандидат пришёл из XtreamVodClient.fetchLiveStreams()
    // (см. ChannelStreamEntity.externalStreamId). Всегда null для M3U —
    // там его физически нет, EPG для таких каналов только через XMLTV.
    val externalStreamId: String? = null,
    // PROMPT_EPG.md, подзадача 5 — см. ChannelStreamEntity.catchupDays/
    // catchupTemplate, ровно тот же смысл, просто на кандидате ДО того,
    // как он станет ChannelStreamEntity.
    val catchupDays: Int = 0,
    val catchupTemplate: String? = null,
    // PROMPT_EPG.md, подзадача 6 — см. ChannelEntity.sortOrder (тот же
    // номер, до превращения кандидата в канал).
    val channelNumber: Int? = null
)

/**
 * PROMPT_IPTV_FOUNDATION.md, подзадача "Сопоставление каналов между
 * источниками" — продуктовое решение: НЕ по тексту названия (риск
 * склеить разные часовые пояса/регионы), а по tvg-id, если он есть и
 * совпал с уже существующим каналом. Если tvg-id нет или не совпал ни с
 * чем — новый Channel с isSubscribed = false, слияние с существующим
 * каналом — только вручную через UI (следующая подзадача), никогда не
 * автоматической эвристикой по имени.
 */
class ChannelMatchingRepository(
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao
) {
    /**
     * Обрабатывает полный список каналов ОДНОГО источника за один refresh().
     * Тот же принцип "не чистим кэш, пока не убедились", что и в
     * PlaylistSourceRepository.refresh() для playlist_movies — пустой
     * список ничего не удаляет (транзиентная ошибка сети/парсинга не
     * должна стирать уже накопленные каналы этого источника).
     */
    // ФИКС (аудит): теперь возвращает только что записанные ChannelStreamEntity
    // (channelId + externalStreamId) — PlaylistSourceRepository.refresh()
    // использует это для fallback-EPG (get_short_epg по каждому каналу),
    // когда у панели нет xmltv.php. Раньше matchAndStore() ничего не
    // возвращал, и единственный уже готовый способ узнать channelId по
    // Xtream stream_id пришлось бы писать заново отдельным запросом.
    suspend fun matchAndStore(sourceId: String, candidates: List<RawChannelCandidate>): List<ChannelStreamEntity> {
        if (candidates.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            // Health-check данные (lastCheckedAt/lastCheckStatus) и ручной
            // priority конкретного стрима нужно перенести на новые записи —
            // иначе каждый refresh() обнулял бы результат health-check
            // (следующая подзадача этой темы) обратно в "unknown".
            val previousStreams = channelStreamDao.getBySourceId(sourceId).associateBy { it.id }

            val newStreams = candidates.map { candidate ->
                val channelId = resolveChannelId(sourceId, candidate)
                val streamId = "cs_${sourceId}_$channelId"
                val previous = previousStreams[streamId]
                ChannelStreamEntity(
                    id = streamId,
                    channelId = channelId,
                    sourceId = sourceId,
                    streamUrl = candidate.streamUrl,
                    rawTitle = candidate.name,
                    userAgent = candidate.userAgent,
                    referrer = candidate.referrer,
                    lastCheckedAt = previous?.lastCheckedAt,
                    lastCheckStatus = previous?.lastCheckStatus ?: "unknown",
                    consecutiveFailures = previous?.consecutiveFailures ?: 0,
                    priority = previous?.priority ?: 0,
                    // Не falls back на previous, в отличие от health-check-полей
                    // выше: candidate — это ВСЕГДА свежий ответ панели ЭТОГО
                    // refresh(), а не что-то, что можно потерять между
                    // обновлениями (в отличие от lastCheckStatus, который
                    // пишет отдельный ChannelHealthCheckWorker, а не refresh()).
                    externalStreamId = candidate.externalStreamId,
                    catchupDays = candidate.catchupDays,
                    catchupTemplate = candidate.catchupTemplate
                )
            }

            channelStreamDao.deleteBySourceId(sourceId)
            channelStreamDao.upsertAll(newStreams)
            newStreams
        }
    }

    /**
     * Возвращает id канонического Channel для этого сырого кандидата,
     * создавая его при первом появлении. Схема id намеренно детерминирована
     * (не UUID), чтобы повторный refresh() того же источника переиспользовал
     * ТУ ЖЕ запись, а не плодил дубли при каждом обновлении:
     *
     * - есть tvg-id → id стабилен ГЛОБАЛЬНО ("ch_tvg_<tvgId>"), это и есть
     *   механизм сопоставления между разными источниками — второй источник
     *   с тем же tvg-id попадёт на тот же Channel автоматически.
     * - нет tvg-id → id стабилен только В РАМКАХ этого источника
     *   ("ch_unmatched_<sourceId>_<slug>") — сознательно не пытаемся угадать
     *   совпадение с каналом другого источника по названию, это должен
     *   подтвердить пользователь через UI слияния (следующая подзадача).
     */
    private suspend fun resolveChannelId(sourceId: String, candidate: RawChannelCandidate): String {
        val channelId = if (candidate.tvgId != null) {
            "ch_tvg_${candidate.tvgId}"
        } else {
            "ch_unmatched_${sourceId}_${slug(candidate.name)}"
        }

        val existing = channelDao.getById(channelId)
        if (existing == null) {
            channelDao.upsert(
                ChannelEntity(
                    id = channelId,
                    canonicalName = candidate.name,
                    tvgId = candidate.tvgId,
                    logo = candidate.logo,
                    category = candidate.category,
                    isSubscribed = false,
                    // PROMPT_EPG.md, подзадача 6 — явный tvg-chno/num, если
                    // источник его дал; иначе следующий свободный номер, а
                    // не 0 (0 у всех несопоставленных каналов сразу сделал
                    // бы номер бесполезным для заппинга — все "0" неотличимы
                    // друг от друга). Присваивается ОДИН РАЗ при первом
                    // появлении канала, дальше не пересчитывается (см. ветку
                    // ниже) — тот же принцип стабильности, что и у самого id.
                    sortOrder = candidate.channelNumber ?: ((channelDao.getMaxSortOrder() ?: 0) + 1)
                )
            )
        } else {
            channelDao.upsert(
                existing.copy(
                    logo = candidate.logo ?: existing.logo,
                    category = candidate.category ?: existing.category,
                    // Обновляем, только если источник явно прислал номер И
                    // он отличается — источник авторитетен для ЯВНОГО
                    // номера (провайдер мог перенумеровать канал), но
                    // молчание источника (candidate.channelNumber == null)
                    // никогда не должно откатывать уже присвоенный
                    // авто-номер обратно в неопределённость.
                    sortOrder = candidate.channelNumber ?: existing.sortOrder
                )
            )
        }
        return channelId
    }

    private fun slug(name: String): String =
        name.trim().lowercase()
            .replace(Regex("[^a-zа-яё0-9]+"), "_")
            .trim('_')
            .ifBlank { "unnamed" }
}
