package com.platinum.ott.ui.theme

import androidx.compose.ui.graphics.Color

// Палитра приведена к иконке/TV-баннеру (фон #0B1220, акцент #4FD1FF,
// светлый текст #EAF6FF) — приложение и иконка теперь один бренд.
// Приложение только тёмное: светлую тему убрали (белый/серый текст был
// зашит в экраны напрямую, светлая схема давала нечитаемые карточки).

// Brand
val ZenithPrimary = Color(0xFF4FD1FF)        // акцент (голубой)
val ZenithPrimaryVariant = Color(0xFF2FB8E6)
val ZenithSecondary = Color(0xFF7C8CFF)      // мягкий индиго
val ZenithError = Color(0xFFFF6B6B)

// Dark theme
val ZenithBackground = Color(0xFF0B1220)
val ZenithSurface = Color(0xFF121B2E)
val ZenithSurfaceVariant = Color(0xFF1B2740)
val ZenithOnPrimary = Color(0xFF06202B)      // тёмный текст на голубом — читается
val ZenithOnSurface = Color(0xFFEAF6FF)
val ZenithOnSurfaceVariant = Color(0xFF9DB0C8)

// Semantic
val ZenithSuccess = Color(0xFF4CAF50)
val ZenithWarning = Color(0xFFFFC107)
val ZenithFavorite = Color(0xFFFF4081)

// Приглушённый вариант primary — использовался как хардкод в SettingsScreen
// для значения настройки под фиолетовым заголовком (не подходит ни под один
// слот ColorScheme, поэтому отдельный токен, а не примесь alpha на лету).
val ZenithPrimaryMuted = Color(0xFFA8E6FF)

// TV-фокус: подсветка фокусируемых Surface на пульте (ClickableSurfaceDefaults).
// Раньше — россыпь Color.White.copy(alpha = ...) с разбросом 0.05–0.08 и
// 0.15–0.18 по разным экранам, здесь стандартизовано до двух значений.
val ZenithFocusContainer = Color(0x14FFFFFF) // ~8% white — неактивный фон
val ZenithFocusContainerActive = Color(0x2EFFFFFF) // ~18% white — под фокусом
