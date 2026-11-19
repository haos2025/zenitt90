package com.platinum.ott.data.repository

import android.content.Context
import org.json.JSONArray

/**
 * Ручные правки каналов, которые не должны затираться обновлением источника:
 *  • каналы, которым пользователь сам сменил категорию или номер/позицию — при следующем refresh() источника
 *    их категория и номер остаются как выбрал пользователь (см. ChannelMatchingRepository.resolveChannelId);
 *  • свой порядок категорий (имена в том порядке, в котором пользователь их расставил).
 * Хранится локально в SharedPreferences; между устройствами пока не синхронизируется.
 */
class ChannelUserEditsStore(context: Context) {
    private val prefs = context.getSharedPreferences("channel_user_edits", Context.MODE_PRIVATE)
    private val lock = Any()
    private val categoryLocked: MutableSet<String> = loadSet("category_locked")
    private val orderLocked: MutableSet<String> = loadSet("order_locked")
    private var categoryOrder: List<String> = loadList("category_order")

    private fun loadList(key: String): List<String> = try {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }

    private fun loadSet(key: String): MutableSet<String> = loadList(key).toMutableSet()

    private fun save() {
        prefs.edit()
            .putString("category_locked", JSONArray(categoryLocked.toList()).toString())
            .putString("order_locked", JSONArray(orderLocked.toList()).toString())
            .putString("category_order", JSONArray(categoryOrder).toString())
            .apply()
    }

    fun isCategoryLocked(channelId: String): Boolean = synchronized(lock) { channelId in categoryLocked }
    fun isOrderLocked(channelId: String): Boolean = synchronized(lock) { channelId in orderLocked }

    fun lockCategory(ids: Collection<String>) = synchronized(lock) { if (categoryLocked.addAll(ids)) save() }
    fun lockOrder(ids: Collection<String>) = synchronized(lock) { if (orderLocked.addAll(ids)) save() }

    /** Канал удалён — его правки больше не нужны. */
    fun forget(channelId: String) = synchronized(lock) {
        val a = categoryLocked.remove(channelId)
        val b = orderLocked.remove(channelId)
        if (a || b) save()
    }

    fun categoryOrder(): List<String> = synchronized(lock) { categoryOrder }

    fun setCategoryOrder(order: List<String>) = synchronized(lock) {
        categoryOrder = order.distinct()
        save()
    }

    /** Категорию переименовали — то же имя в своём порядке категорий. */
    fun renameInCategoryOrder(old: String, new: String) = synchronized(lock) {
        categoryOrder = categoryOrder.map { if (it == old) new else it }.distinct()
        save()
    }
}
