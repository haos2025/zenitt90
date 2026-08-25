package com.platinum.ott.core.platform

/**
 * TMDB отдаёт постеры и backdrop'ы не "по запросу произвольного размера", а
 * фиксированными предустановленными "корзинами" ширины. Если всегда брать
 * "original" — это может быть скан 2000px+ на карточку 140dp, лишние мегабайты
 * трафика и лишняя память на декодирование. Если жёстко зашить один маленький
 * размер — на плотных экранах (TV, xxhdpi/xxxhdpi телефоны) постер будет мылить.
 *
 * Здесь выбирается ближайшая корзина, которая не меньше нужной ширины в px
 * (px = dp карточки * плотность экрана), то есть именно "адаптация по DPI":
 * чем плотнее экран или чем крупнее карточка, тем больше корзина.
 */
object TmdbImage {
    private const val BASE_URL = "https://image.tmdb.org/t/p/"
    private val posterBuckets = listOf(92, 154, 185, 342, 500, 780)
    private val backdropBuckets = listOf(300, 780, 1280)
    // TMDB отдаёт фото персон отдельными, более узкими корзинами, чем
    // постеры/backdrop — w45/w185/h632/original. h632 сюда не включаем:
    // это "по высоте", а не "по ширине", buildUrl ниже работает только с
    // корзинами по ширине, смешивать не стоит — для карточки актёра в
    // ряду (маленькое фото) w45/w185 достаточно, до original не дойдёт.
    private val profileBuckets = listOf(45, 185)

    fun posterUrl(path: String?, targetWidthPx: Int): String? =
        buildUrl(path, targetWidthPx, posterBuckets)

    fun backdropUrl(path: String?, targetWidthPx: Int): String? =
        buildUrl(path, targetWidthPx, backdropBuckets)

    // PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4 — карусель актёров.
    fun profileUrl(path: String?, targetWidthPx: Int): String? =
        buildUrl(path, targetWidthPx, profileBuckets)

    private fun buildUrl(path: String?, targetWidthPx: Int, buckets: List<Int>): String? {
        if (path.isNullOrBlank()) return null
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val bucket = buckets.firstOrNull { it >= targetWidthPx }
        val sizeSegment = if (bucket != null) "w$bucket" else "original"
        return "$BASE_URL$sizeSegment$normalizedPath"
    }
}
