package com.platinum.ott.domain.model

// source — раньше пользователь видел только "1080p"/"720p" без понятия,
// откуда вариант. Дефолт "Backend" сохраняет обратную совместимость со
// старыми вызовами StreamVariant(quality, url) без третьего аргумента.
data class StreamVariant(val quality: String, val url: String, val source: String = "Backend")
