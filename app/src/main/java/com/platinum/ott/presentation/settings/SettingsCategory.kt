package com.platinum.ott.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Категории настроек (план 09, раздел 3). Один и тот же список, порядок и названия
 * на ТВ (две панели) и на телефоне (список → страница).
 *
 * Порядок — от частого к редкому. [id] идёт в маршрут телефона (`settings_page/{id}`),
 * поэтому не менять уже выпущенные значения. Бывший раздел «Плеер» (id "player") влит в
 * «Воспроизведение»; старый id безопасно падает на PLAYBACK через [fromId].
 */
enum class SettingsCategory(val id: String, val title: String, val subtitle: String, val icon: ImageVector) {
    PLAYBACK("playback", "Воспроизведение", "Качество, субтитры, плеер, пульт", Icons.Outlined.PlayCircle),
    APPEARANCE("appearance", "Внешний вид", "Оформление интерфейса", Icons.Outlined.Palette),
    NETWORK("network", "Сеть и данные", "Таймаут, мобильные данные, кэш, устройства", Icons.Outlined.Wifi),
    NOTIFICATIONS("notifications", "Уведомления", "Новые серии", Icons.Outlined.Notifications),
    SOURCES("sources", "Источники", "Плейлисты, плагины, телепрограмма", Icons.Outlined.Source),
    MAINTENANCE("maintenance", "Обслуживание", "Парсеры, о приложении", Icons.Outlined.Build);

    companion object {
        fun fromId(id: String?): SettingsCategory =
            // Бывший раздел «Устройства» влит в «Сеть и данные».
            if (id == "devices") NETWORK else entries.firstOrNull { it.id == id } ?: PLAYBACK
    }
}
