package com.platinum.ott.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val ZenithPrimary = Color(0xFF6C63FF)
val ZenithPrimaryVariant = Color(0xFF5A52E0)
val ZenithSecondary = Color(0xFF03DAC6)
val ZenithError = Color(0xFFFF6B6B)

// Dark theme
val ZenithBackground = Color(0xFF101010)
val ZenithSurface = Color(0xFF1C1C1C)
val ZenithSurfaceVariant = Color(0xFF2A2A2A)
val ZenithOnPrimary = Color(0xFFFFFFFF)
val ZenithOnSurface = Color(0xFFE0E0E0)
val ZenithOnSurfaceVariant = Color(0xFFAAAAAA)

// Light theme
val ZenithLightBackground = Color(0xFFF5F5F5)
val ZenithLightSurface = Color(0xFFFFFFFF)
val ZenithLightSurfaceVariant = Color(0xFFE8E8E8)
val ZenithLightOnSurface = Color(0xFF1C1C1C)
val ZenithLightOnSurfaceVariant = Color(0xFF666666)

// Semantic
val ZenithSuccess = Color(0xFF4CAF50)
val ZenithWarning = Color(0xFFFFC107)
val ZenithFavorite = Color(0xFFFF4081)

// Приглушённый вариант primary — использовался как хардкод в SettingsScreen
// для значения настройки под фиолетовым заголовком (не подходит ни под один
// слот ColorScheme, поэтому отдельный токен, а не примесь alpha на лету).
val ZenithPrimaryMuted = Color(0xFFB8B4FF)

// TV-фокус: подсветка фокусируемых Surface на пульте (ClickableSurfaceDefaults).
// Раньше — россыпь Color.White.copy(alpha = ...) с разбросом 0.05–0.08 и
// 0.15–0.18 по разным экранам, здесь стандартизовано до двух значений.
val ZenithFocusContainer = Color(0x14FFFFFF) // ~8% white — неактивный фон
val ZenithFocusContainerActive = Color(0x2EFFFFFF) // ~18% white — под фокусом
