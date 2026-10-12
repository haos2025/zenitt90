package com.platinum.ott.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ZenithTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

/**
 * Масштаб текста под экран ТВ (смотрят с 2–3 метров): основной текст,
 * подписи и мелкие метки крупнее, чем на телефоне. Заголовки (display/
 * headline) остаются как есть. Применяется и к material3-текстам внутри
 * TV-экранов (диалоги и т.п.), и к tv-material3 (см. ZenithTvTypography).
 */
fun zenithTypography(isTv: Boolean): Typography =
    if (!isTv) ZenithTypography else ZenithTypography.copy(
        titleLarge = ZenithTypography.titleLarge.copy(fontSize = 20.sp),
        titleMedium = ZenithTypography.titleMedium.copy(fontSize = 18.sp),
        bodyLarge = ZenithTypography.bodyLarge.copy(fontSize = 19.sp),
        bodyMedium = ZenithTypography.bodyMedium.copy(fontSize = 17.sp),
        bodySmall = ZenithTypography.bodySmall.copy(fontSize = 15.sp),
        labelLarge = ZenithTypography.labelLarge.copy(fontSize = 17.sp),
        labelMedium = ZenithTypography.labelMedium.copy(fontSize = 15.sp),
        labelSmall = ZenithTypography.labelSmall.copy(fontSize = 13.sp),
    )

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
val ZenithTvTypography = androidx.tv.material3.Typography(
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)
