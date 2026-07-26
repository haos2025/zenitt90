package com.platinum.ott.domain.model

// source — раньше пользователь видел только "1080p"/"720p" без понятия,
// откуда вариант. Дефолт "Backend" сохраняет обратную совместимость со
// старыми вызовами StreamVariant(quality, url) без третьего аргумента.
// headers — раньше терялись #EXTVLCOPT-заголовки конкретного M3U-канала
// (http-user-agent/http-referrer) — многие каналы без НИХ отдают 404/403
// от источника, общий User-Agent на все каналы сразу это не покрывает.
data class StreamVariant(
    val quality: String,
    val url: String,
    val source: String = "Backend",
    val headers: Map<String, String> = emptyMap()
)
