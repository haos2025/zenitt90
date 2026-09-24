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
    val healthStatus: String,
    // PROMPT_EPG.md, подзадача 5 — максимум по всем ChannelStreamEntity
    // этого канала (тот же принцип агрегата, что и healthStatus выше): 0 —
    // ни один источник этого канала архив не заявляет. Максимум, не
    // "любой" — сетка (EpgGridScreen) считает программу доступной для
    // архива по САМОМУ ЩЕДРОМУ источнику канала, конкретный вариант с
    // подходящим catchupDays для программы всё равно отбирает
    // GetPlayableUrlUseCase.executeChannelCatchup() по месту.
    val catchupDaysAvailable: Int,
    // PROMPT_EPG.md, подзадача 6 — ChannelEntity.sortOrder, тот же номер,
    // что и в списке "Каналы" (см. комментарий у поля в ChannelEntity.kt).
    val channelNumber: Int
)

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao
) {
    suspend fun getAll(): List<ChannelUiItem> = withContext(Dispatchers.IO) { toUiItems(channelDao.getAll()) }

    // PROMPT_EPG.md, подзадача 4 (сетка программ) — только подписанные,
    // не весь каталог: сетка по определению показывает "мои каналы", как
    // и сам список каналов в разделе Настройки (см. комментарий у
    // ChannelsViewModel.load() про сортировку "подписанные — сначала").
    suspend fun getSubscribed(): List<ChannelUiItem> = withContext(Dispatchers.IO) { toUiItems(channelDao.getSubscribed()) }

    // Общая часть getAll()/getSubscribed() — раньше (до этой подзадачи)
    // была только внутри getAll(), дублировать её ради getSubscribed()
    // означало бы либо копипасту, либо расхождение в будущем.
    private suspend fun toUiItems(channels: List<com.platinum.ott.data.local.entity.ChannelEntity>): List<ChannelUiItem> {
        // ФИКС (аудит): один батч-запрос вместо N — см. комментарий у
        // ChannelStreamDao.getByChannelIds().
        val streamsByChannel = channelStreamDao.getByChannelIds(channels.map { it.id }).groupBy { it.channelId }
        return channels.map { channel ->
            val streams = streamsByChannel[channel.id].orEmpty()
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
                },
                catchupDaysAvailable = streams.maxOfOrNull { it.catchupDays } ?: 0,
                channelNumber = channel.sortOrder
            )
        }
    }

    // PROMPT_EPG.md, подзадача 6 — заппинг по номеру, используется
    // PlayerViewModel.zapToChannelNumber(). Оборачивает найденный
    // ChannelEntity через toUiItems(), а не строит ChannelUiItem вручную —
    // чтобы healthStatus/streamCount/catchupDaysAvailable считались тем же
    // кодом, что и везде, не отдельной (и рискующей разойтись) копией.
    suspend fun getSubscribedByNumber(number: Int): ChannelUiItem? = withContext(Dispatchers.IO) {
        channelDao.getSubscribedByNumber(number)?.let { toUiItems(listOf(it)).firstOrNull() }
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
     * же источника — после переноса у target оказывались два разных id,
     * указывающих на тот же (sourceId, физический стрим). Раньше это было
     * отмечено как известное ограничение ("сама не исчезнет") — теперь
     * дочищаем сразу: группируем по (sourceId, streamUrl), из каждой
     * группы дублей оставляем один (с наивысшим приоритетом), остальные
     * удаляем.
     */
    suspend fun merge(sourceChannelId: String, targetChannelId: String) = withContext(Dispatchers.IO) {
        if (sourceChannelId == targetChannelId) return@withContext
        channelStreamDao.reassignChannel(sourceChannelId, targetChannelId)
        channelDao.deleteById(sourceChannelId)

        val duplicateIds = channelStreamDao.getByChannelId(targetChannelId)
            .groupBy { it.sourceId to it.streamUrl }
            .values
            .filter { it.size > 1 }
            .flatMap { group -> group.sortedBy { it.priority }.drop(1).map { it.id } }
        if (duplicateIds.isNotEmpty()) channelStreamDao.deleteByIds(duplicateIds)
    }
}
