package com.platinum.ott.data.playlist

import com.platinum.ott.data.local.entity.PlaylistMovieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryClassifierTest {

    private fun entry(
        url: String,
        title: String = "Название",
        duration: Int? = null,
        group: String? = null,
        tvgId: String? = null,
        chno: Int? = null,
        catchupDays: Int? = null
    ) = ParsedEntry(
        entity = PlaylistMovieEntity(
            id = "m3u_" + title.hashCode(), title = title, year = 0, poster = null,
            genre = group ?: "Мой плейлист", streamUrl = url,
            tvgId = tvgId, channelNumber = chno, catchupDays = catchupDays
        ),
        durationSec = duration,
        rawGroup = group
    )

    private fun auto(vararg e: ParsedEntry, overrides: Map<String, String> = emptyMap()) =
        EntryClassifier.classifyAll(e.toList(), EntryClassifier.MODE_AUTO, overrides)

    @Test fun `duration minus one is a channel`() {
        assertEquals(listOf(EntryKind.CHANNEL), auto(entry("http://h/stream", duration = -1)))
    }

    @Test fun `positive duration is vod`() {
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/stream", duration = 5400)))
    }

    @Test fun `video extension is vod even with minus one duration`() {
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/film.mkv?token=1", duration = -1)))
    }

    @Test fun `m3u8 with minus one and tvg-id is a channel`() {
        assertEquals(listOf(EntryKind.CHANNEL), auto(entry("http://h/x.m3u8", duration = -1, tvgId = "first")))
    }

    @Test fun `live path and channel number are channel`() {
        assertEquals(listOf(EntryKind.CHANNEL), auto(entry("http://h/live/u/p/1.ts", duration = -1)))
        assertEquals(listOf(EntryKind.CHANNEL), auto(entry("http://h/a", chno = 5)))
    }

    @Test fun `movie and series paths are vod`() {
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/movie/u/p/1")))
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/series/u/p/1")))
    }

    @Test fun `episode marker in title is vod`() {
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/a", title = "Шоу S01E02")))
    }

    @Test fun `strong channel beats weak vod-ish nothing - catchup`() {
        assertEquals(listOf(EntryKind.CHANNEL), auto(entry("http://h/a", catchupDays = 3)))
    }

    @Test fun `conflict falls back to group majority`() {
        val kino = "Кино"
        val result = auto(
            entry("http://h/1.mp4", group = kino, title = "A"),
            entry("http://h/2.mp4", group = kino, title = "B"),
            // сильный канал (/live/) + сильный фильм (.mp4) — конфликт, группа решает
            entry("http://h/live/3.mp4", group = kino, title = "C")
        )
        assertEquals(listOf(EntryKind.VOD, EntryKind.VOD, EntryKind.VOD), result)
    }

    @Test fun `undecided entry takes group majority`() {
        val result = auto(
            entry("http://h/a", group = "Новости", duration = -1, title = "A"),
            entry("http://h/b", group = "Новости", duration = -1, title = "B"),
            entry("http://h/c", group = "Новости", title = "C") // без признаков
        )
        assertEquals(listOf(EntryKind.CHANNEL, EntryKind.CHANNEL, EntryKind.CHANNEL), result)
    }

    @Test fun `no signs and no group is vod`() {
        assertEquals(listOf(EntryKind.VOD), auto(entry("http://h/a")))
    }

    @Test fun `group override beats everything`() {
        val result = auto(
            entry("http://h/1.mp4", group = "Спорт", duration = 3000),
            entry("http://h/live/2", group = "Кино HD", duration = -1),
            overrides = mapOf("Спорт" to "live", "Кино HD" to "vod")
        )
        assertEquals(listOf(EntryKind.CHANNEL, EntryKind.VOD), result)
    }

    @Test fun `forced modes ignore heuristics`() {
        val e = entry("http://h/film.mkv", duration = 5000)
        assertEquals(listOf(EntryKind.CHANNEL), EntryClassifier.classifyAll(listOf(e), EntryClassifier.MODE_LIVE))
        val c = entry("http://h/live/1", duration = -1)
        assertEquals(listOf(EntryKind.VOD), EntryClassifier.classifyAll(listOf(c), EntryClassifier.MODE_VOD))
    }

    @Test fun `override beats forced mode`() {
        val e = entry("http://h/a", group = "Спорт")
        val r = EntryClassifier.classifyAll(listOf(e), EntryClassifier.MODE_VOD, mapOf("Спорт" to "live"))
        assertEquals(listOf(EntryKind.CHANNEL), r)
    }
}
