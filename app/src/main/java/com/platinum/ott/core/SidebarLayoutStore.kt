package com.platinum.ott.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Пользовательская раскладка боковой панели ТВ: порядок пунктов и скрытые пункты.
 * Хранятся маршруты ("home", "channels" ...), поэтому новые пункты, добавленные в будущих версиях,
 * сами появятся в конце, а удалённые из кода просто игнорируются.
 */
data class SidebarLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet())

object SidebarLayoutStore {
    private val _layout = MutableStateFlow(SidebarLayout())
    val layout: StateFlow<SidebarLayout> = _layout.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("zenith_sidebar", Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = prefs(context)
            _layout.value = SidebarLayout(
                order = p.getString("order", "").orEmpty().split(',').filter { it.isNotBlank() },
                hidden = p.getString("hidden", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            )
        } catch (_: Exception) { /* останется раскладка по умолчанию */ }
    }

    fun update(context: Context, change: (SidebarLayout) -> SidebarLayout) {
        ensureLoaded(context)
        val l = change(_layout.value)
        _layout.value = l
        try {
            prefs(context).edit()
                .putString("order", l.order.joinToString(","))
                .putString("hidden", l.hidden.joinToString(","))
                .apply()
        } catch (_: Exception) { }
    }

    fun reset(context: Context) = update(context) { SidebarLayout() }
}
