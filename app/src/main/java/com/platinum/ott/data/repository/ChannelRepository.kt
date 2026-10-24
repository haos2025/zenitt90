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
    // Скрыт ли канал сам по себе / скрыта ли его категория (см. HiddenChannelsStore). Видимый канал —
    // тот, у которого не скрыто ни то, ни другое (isVisible).
    val isHidden: Boolean,
    val categoryHidden: Boolean,
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
) {
    val isVisible: Boolean get() = !isHidden && !categoryHidden
}

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao,
    private val hiddenStore: HiddenChannelsStore
) {
    // Все каналы, которые не скрыты (ни сами, ни категорией). Подписок на каналы нет, поэтому в базе
    // isSubscribed у всех = 1 — «видимость» целиком определяется HiddenChannelsStore.
    private suspend fun visibleEntities() = channelDao.getSubscribed().filter { hiddenStore.isVisible(it.id, it.category) }

    suspend fun getAll(): List<ChannelUiItem> = withContext(Dispatchers.IO) { toUiItems(channelDao.getAll()) }

    // PROMPT_EPG.md, подзадача 4 (сетка программ) — только подписанные,
    // не весь каталог: сетка по определению показывает "мои каналы", как
    // и сам список каналов в разделе Настройки (см. комментарий у
    // ChannelsViewModel.load() про сортировку "подписанные — сначала").
    suspend fun getSubscribed(): List<ChannelUiItem> = withContext(Dispatchers.IO) { toUiItems(visibleEntities()) }

    // Общая часть getAll()/getSubscribed() — раньше (до этой подзадачи)
    // была только внутри getAll(), дублировать её ради getSubscribed()
    // означало бы либо копипасту, либо расхождение в будущем.
    private suspend fun toUiItems(channels: List<com.platinum.ott.data.local.entity.ChannelEntity>): List<ChannelUiItem> {
        // ФИКС (аудит): один батч-запрос вместо N — см. комментарий у
        // ChannelStreamDao.getByChannelIds().
        // ФИКС (вылет «Каналы» после добавления плейлиста): SQLite ограничивает число
        // параметров одного запроса (999 на Android ≤ 10 — это и ТВ-приставки). Список
        // из тысячи+ каналов давал `too many SQL variables` и падение приложения,
        // поэтому идентификаторы отправляем пачками по 500.
        val streamsByChannel = channels.map { it.id }.chunked(500)
            .flatMap { channelStreamDao.getByChannelIds(it) }
            .groupBy { it.channelId }
        return channels.map { channel ->
            val streams = streamsByChannel[channel.id].orEmpty()
            ChannelUiItem(
                id = channel.id,
                canonicalName = channel.canonicalName,
                logo = channel.logo,
                category = channel.category,
                regionHint = channel.regionHint,
                isHidden = hiddenStore.isChannelHidden(channel.id),
                categoryHidden = hiddenStore.isCategoryHidden(channel.category),
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
        visibleEntities().firstOrNull { it.sortOrder == number }?.let { toUiItems(listOf(it)).firstOrNull() }
    }

    // НОВОЕ (заппинг стрелками вверх/вниз во время просмотра): следующий/
    // предыдущий подписанный канал по channelNumber, с закольцовкой (после
    // последнего — снова первый, и наоборот). Тот же принцип, что и у
    // getSubscribedByNumber() выше — переиспользует toUiItems(), не строит
    // ChannelUiItem вручную.
    //
    // filter — ограничение переключения выбранной категорией (см. PlayerViewModel.zap()). Применяется,
    // только если текущий канал сам проходит фильтр: канал, открытый из истории или по ссылке и не
    // входящий в выбранную категорию, листается по всем видимым, а не «застревает».
    suspend fun getAdjacentSubscribed(
        currentChannelId: String,
        forward: Boolean,
        filter: (ChannelUiItem) -> Boolean = { true }
    ): ChannelUiItem? = withContext(Dispatchers.IO) {
        val all = toUiItems(visibleEntities()).sortedBy { it.channelNumber }
        val current = all.firstOrNull { it.id == currentChannelId }
        val sorted = if (current != null && filter(current)) all.filter(filter) else all
        if (sorted.isEmpty()) return@withContext null
        val currentIndex = sorted.indexOfFirst { it.id == currentChannelId }
        if (currentIndex == -1) return@withContext sorted.firstOrNull()
        val nextIndex = if (forward) (currentIndex + 1) % sorted.size else (currentIndex - 1 + sorted.size) % sorted.size
        sorted[nextIndex]
    }

    suspend fun getById(channelId: String): ChannelUiItem? = withContext(Dispatchers.IO) {
        channelDao.getById(channelId)?.let { toUiItems(listOf(it)).firstOrNull() }
    }

    suspend fun getSubscribedCount(): Int = withContext(Dispatchers.IO) { visibleEntities().size }

    /** Скрыть/показать один канал (запоминается и синхронизируется между устройствами). */
    fun setHidden(channelId: String, hidden: Boolean) = hiddenStore.setChannelHidden(channelId, hidden)

    /** Скрыть/показать всю категорию — в том числе каналы, которые появятся в ней позже. */
    fun setCategoryHidden(category: String?, hidden: Boolean) = hiddenStore.setCategoryHidden(category, hidden)

    fun hiddenCategoryNames(): List<String> = hiddenStore.hiddenCategoryNames()

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
        duplicateIds.chunked(500).forEach { channelStreamDao.deleteByIds(it) }
    }
}
