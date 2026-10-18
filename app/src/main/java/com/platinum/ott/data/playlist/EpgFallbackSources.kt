package com.platinum.ott.data.playlist

/**
 * Запасные источники программы передач (XMLTV, gzip определяется автоматически).
 * Используются, когда у плейлиста нет своего url-tvg или он ничего не дал.
 * Порядок = приоритет. Список взят из открытых справочников по IPTV; доступность
 * на момент написания проверить не удалось — при недоступности источник молча
 * пропускается, берётся следующий. Менять список можно прямо здесь.
 */
object EpgFallbackSources {
    val URLS = listOf(
        "https://iptvx.one/epg/epg.xml.gz",
        "https://www.teleguide.info/download/new3/xmltv.xml.gz",
        "https://epg.one/epg.xml.gz"
    )
}
