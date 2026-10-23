package com.platinum.ott.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCategoryTest {

    @Test
    fun hasNineCategoriesInPlannedOrder() {
        assertEquals(
            listOf("playback", "player", "appearance", "network", "notifications", "sources", "plugins", "devices", "maintenance"),
            SettingsCategory.entries.map { it.id }
        )
    }

    @Test
    fun idsAreUniqueBecauseTheyGoIntoPhoneRoute() {
        val ids = SettingsCategory.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // id попадает в маршрут settings_page/{cat}: без слэшей и пробелов.
        assertTrue(ids.all { it.matches(Regex("[a-z]+")) })
    }

    @Test
    fun fromIdRoundTripsAndFallsBackToFirstCategory() {
        SettingsCategory.entries.forEach { assertEquals(it, SettingsCategory.fromId(it.id)) }
        assertEquals(SettingsCategory.PLAYBACK, SettingsCategory.fromId(null))
        assertEquals(SettingsCategory.PLAYBACK, SettingsCategory.fromId("нет-такой"))
    }

    @Test
    fun titlesAndSubtitlesAreFilled() {
        SettingsCategory.entries.forEach {
            assertTrue("${it.id}: пустое название", it.title.isNotBlank())
            assertTrue("${it.id}: пустое описание", it.subtitle.isNotBlank())
        }
    }
}
