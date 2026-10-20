package com.platinum.ott.data.repository

import android.content.Context
import com.platinum.ott.data.remote.dto.HiddenItemDto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Скрытые каналы и скрытые категории (заменили подписки на каналы, 21.10.2026).
 *
 * Хранится в SharedPreferences как список записей «ключ → скрыт/показан + время изменения»; запись
 * «показан» не удаляется (надгробие) — иначе синхронизация между устройствами не смогла бы отличить
 * «показал» от «никогда не скрывал». При слиянии побеждает запись с более поздним временем.
 * Видимость считается при показе: канал виден, если он сам не скрыт и его категория не скрыта —
 * поэтому НОВЫЕ каналы в скрытой категории тоже не показываются и канал не обязан уже лежать в базе.
 */
class HiddenChannelsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_channels", Context.MODE_PRIVATE)

    private data class Entry(val name: String, val hidden: Boolean, val updatedAt: Long)

    private val lock = Any()
    private var channels: MutableMap<String, Entry> = load("channels")
    private var categories: MutableMap<String, Entry> = load("categories")

    private fun load(key: String): MutableMap<String, Entry> {
        val out = LinkedHashMap<String, Entry>()
        try {
            val obj = JSONObject(prefs.getString(key, "{}") ?: "{}")
            obj.keys().forEach { k ->
                val e = obj.getJSONObject(k)
                out[k] = Entry(e.optString("n", k), e.optBoolean("h", false), e.optLong("t", 0L))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun save() {
        fun toJson(m: Map<String, Entry>) = JSONObject().also { o ->
            m.forEach { (k, e) -> o.put(k, JSONObject().put("n", e.name).put("h", e.hidden).put("t", e.updatedAt)) }
        }
        prefs.edit().putString("channels", toJson(channels).toString()).putString("categories", toJson(categories).toString()).apply()
    }

    /** Ключ категории: без регистра и лишних пробелов; пустая — «Без категории». */
    fun categoryKey(category: String?): String = (category?.trim()?.ifBlank { null } ?: NO_CATEGORY).lowercase()

    fun isChannelHidden(channelId: String): Boolean = synchronized(lock) { channels[channelId]?.hidden == true }
    fun isCategoryHidden(category: String?): Boolean = synchronized(lock) { categories[categoryKey(category)]?.hidden == true }

    /** Виден ли канал: сам не скрыт и категория не скрыта. */
    fun isVisible(channelId: String, category: String?): Boolean = !isChannelHidden(channelId) && !isCategoryHidden(category)

    fun setChannelHidden(channelId: String, hidden: Boolean) = synchronized(lock) {
        channels[channelId] = Entry(channelId, hidden, System.currentTimeMillis())
        save()
    }

    fun setCategoryHidden(category: String?, hidden: Boolean) = synchronized(lock) {
        val display = category?.trim()?.ifBlank { null } ?: NO_CATEGORY
        categories[categoryKey(category)] = Entry(display, hidden, System.currentTimeMillis())
        save()
    }

    /** Названия скрытых категорий (для списка «Скрытые категории»). */
    fun hiddenCategoryNames(): List<String> = synchronized(lock) {
        categories.values.filter { it.hidden }.map { it.name }.sortedBy { it.lowercase() }
    }

    // ---------- Синхронизация ----------

    fun snapshot(): List<HiddenItemDto> = synchronized(lock) {
        channels.map { (k, e) -> HiddenItemDto(KIND_CHANNEL, k, e.name, e.hidden, e.updatedAt) } +
            categories.map { (k, e) -> HiddenItemDto(KIND_CATEGORY, k, e.name, e.hidden, e.updatedAt) }
    }

    /** Слияние присланных записей: побеждает более поздняя. Возвращает true, если что-то изменилось. */
    fun merge(items: List<HiddenItemDto>?): Boolean = synchronized(lock) {
        var changed = false
        for (it in items.orEmpty()) {
            val target = when (it.kind) { KIND_CHANNEL -> channels; KIND_CATEGORY -> categories; else -> continue }
            val local = target[it.key]
            if (local == null || it.updatedAt > local.updatedAt) {
                target[it.key] = Entry(it.name, it.hidden, it.updatedAt)
                changed = true
            }
        }
        if (changed) save()
        changed
    }

    companion object {
        const val NO_CATEGORY = "Без категории"
        const val KIND_CHANNEL = "channel"
        const val KIND_CATEGORY = "category"
    }
}
