package com.platinum.ott.data.repository

/**
 * Категории каналов и «категория переключения» — в одном месте. Раньше строки «__all__», «__fav__»,
 * «Без категории» и имена настроек дублировались в экране каналов, шторке плеера и PlayerViewModel.
 * Чистая логика без Android — проверяется на JVM.
 *
 * «Сырое» значение категории ([raw]): [ALL], [FAV] или имя группы.
 */
object ChannelCategoryFilter {
    const val ALL = "__all__"
    const val FAV = "__fav__"
    const val NO_CATEGORY = "Без категории"

    const val PREFS_PHONE = "phone_channels"
    const val PREFS_TV = "tv_channels"
    const val KEY_CATEGORY = "last_category"
    const val KEY_FAVS = "favorite_ids"

    /** Категория канала; пустая или пробельная — [NO_CATEGORY]. */
    fun categoryOf(ch: ChannelUiItem): String = ch.category?.trim()?.ifBlank { null } ?: NO_CATEGORY

    fun matches(raw: String, favorites: Set<String>, ch: ChannelUiItem): Boolean = when (raw) {
        ALL -> true
        FAV -> ch.id in favorites
        else -> categoryOf(ch) == raw
    }

    fun predicate(raw: String, favorites: Set<String>): (ChannelUiItem) -> Boolean = { matches(raw, favorites, it) }

    /** Название для чипа/баннера. */
    fun label(raw: String): String = when (raw) {
        ALL -> "Все каналы"
        FAV -> "★ Избранные"
        else -> raw
    }

    /** Группа каналов с числом каналов и признаком «все каналы группы скрыты» (для режима правки). */
    data class CategoryInfo(val name: String, val count: Int, val allHidden: Boolean)

    /**
     * Категории по каналам: сначала в порядке, который пользователь задал вручную ([order]), остальные —
     * по убыванию числа каналов, при равенстве по имени.
     */
    fun categoriesOf(channels: List<ChannelUiItem>, order: List<String> = emptyList()): List<CategoryInfo> {
        val infos = channels.groupBy { categoryOf(it) }
            .map { (name, list) -> CategoryInfo(name, list.size, list.all { it.categoryHidden }) }
        val pos = order.withIndex().associate { it.value to it.index }
        return infos.sortedWith(
            compareBy<CategoryInfo> { pos[it.name] ?: Int.MAX_VALUE }
                .thenByDescending { it.count }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Запомненная категория могла исчезнуть (источник удалён, категория скрыта) — тогда «Все».
     * [allowFav] = false (режим правки): «Избранные» не показываются, тоже «Все».
     */
    fun resolve(raw: String, categories: List<CategoryInfo>, allowFav: Boolean = true): String = when {
        raw == ALL -> ALL
        raw == FAV -> if (allowFav) FAV else ALL
        categories.any { it.name == raw } -> raw
        else -> ALL
    }

    /**
     * С какой категории открыть шторку списка каналов в плеере: с активной категории переключения, если в ней
     * есть текущий канал; иначе — с категории самого канала (список открывается там, где вы «находитесь»).
     */
    fun sheetStartCategory(active: String, channels: List<ChannelUiItem>, currentId: String, favorites: Set<String>): String {
        val current = channels.firstOrNull { it.id == currentId } ?: return active
        if (matches(active, favorites, current)) return active
        return categoryOf(current)
    }

    /**
     * Соседний канал для переключения CH+/CH−. [allSortedByNumber] — все видимые каналы по номеру.
     * Если текущий канал входит в категорию — следующий/предыдущий в ней с закольцовкой. Если нет (открыт из истории,
     * из шторки другой категории или номером) — ближайший по номеру канал категории вперёд/назад, тоже с кольцом.
     * null — в категории нет каналов.
     */
    fun adjacent(
        allSortedByNumber: List<ChannelUiItem>,
        currentId: String,
        forward: Boolean,
        filter: (ChannelUiItem) -> Boolean
    ): ChannelUiItem? {
        val sorted = allSortedByNumber.filter(filter)
        if (sorted.isEmpty()) return null
        val index = sorted.indexOfFirst { it.id == currentId }
        if (index >= 0) {
            val next = if (forward) (index + 1) % sorted.size else (index - 1 + sorted.size) % sorted.size
            return sorted[next]
        }
        val number = allSortedByNumber.firstOrNull { it.id == currentId }?.channelNumber ?: return sorted.first()
        return if (forward) sorted.firstOrNull { it.channelNumber > number } ?: sorted.first()
        else sorted.lastOrNull { it.channelNumber < number } ?: sorted.last()
    }

    /** «3 из 12» — положение канала в категории (1-based), null если канала в ней нет. */
    fun positionIn(allSortedByNumber: List<ChannelUiItem>, currentId: String, filter: (ChannelUiItem) -> Boolean): Pair<Int, Int>? {
        val sorted = allSortedByNumber.filter(filter)
        val index = sorted.indexOfFirst { it.id == currentId }
        return if (index < 0) null else (index + 1) to sorted.size
    }
}
