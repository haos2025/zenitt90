package com.platinum.ott.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Категории настроек (план 09, раздел 3). Один и тот же список, порядок и названия
 * на ТВ (две панели) и на телефоне (список → страница).
 *
 * Порядок — от частого к редкому. [id] идёт в маршрут телефона (`settings_page/{id}`),
 * поэтому не менять уже выпущенные значения.
 */
enum class SettingsCategory(val id: String, val title: String, val subtitle: String, val icon: ImageVector) {
    PLAYBACK("playback", "Воспроизведение", "Качество, субтитры", Icons.Outlined.PlayCircle),
    PLAYER("player", "Плеер", "Управление, полоска прогресса, пульт", Icons.Outlined.SmartDisplay),
    APPEARANCE("appearance", "Внешний вид", "Оформление интерфейса", Icons.Outlined.Palette),
    NETWORK("network", "Сеть и данные", "Таймаут, мобильные данные, кэш", Icons.Outlined.Wifi),
    NOTIFICATIONS("notifications", "Уведомления", "Новые серии", Icons.Outlined.Notifications),
    SOURCES("sources", "Источники", "Плейлисты, телепрограмма", Icons.Outlined.Source),
    PLUGINS("plugins", "Плагины", "Каталог и управление", Icons.Outlined.Extension),
    DEVICES("devices", "Устройства", "Синхронизация, телефон-компаньон", Icons.Outlined.Devices),
    MAINTENANCE("maintenance", "Обслуживание", "Парсеры, о приложении", Icons.Outlined.Build);

    companion object {
        fun fromId(id: String?): SettingsCategory = entries.firstOrNull { it.id == id } ?: PLAYBACK
    }
}
