package com.platinum.ott.data.playlist

/**
 * Запасные источники программы передач (XMLTV: .xml, .xml.gz или .zip — формат определяется
 * по первым байтам). Используются, когда у плейлиста нет своего url-tvg или он ничего не дал.
 * Порядок = приоритет. Недоступные адреса отсекаются быстрой параллельной проверкой
 * (см. PlaylistSourceRepository.probeEpgUrls), поэтому «мёртвая» ссылка больше не тормозит остальные.
 *
 * Первым стоит облегчённый файл iptvx.one (≈20 МБ вместо ≈60 МБ у полного): на ТВ-приставке
 * полный файл не успевал скачаться и разобраться. Менять список можно прямо здесь.
 */
object EpgFallbackSources {
    val URLS = listOf(
        "https://iptvx.one/epg/epg_lite.xml.gz",
        "https://iptvx.one/epg/epg.xml.gz",
        "https://www.teleguide.info/download/new3/tvguide.zip",
        "https://epg.one/epg.xml.gz"
    )
}
