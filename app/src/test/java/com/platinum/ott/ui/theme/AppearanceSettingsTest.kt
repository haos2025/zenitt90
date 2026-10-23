package com.platinum.ott.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {

    @Test
    fun defaultSizeIsNormalStepWithMultiplierOne() {
        val s = AppearanceSettings()
        assertEquals(1, s.sizeStep)
        assertEquals(1.0f, s.sizeMultiplier, 0f)
        assertEquals(4, SIZE_STEP_TITLES.size)
        assertEquals(SIZE_STEP_TITLES.size, SIZE_STEP_MULTIPLIERS.size)
    }

    @Test
    fun sizeMultiplierIsClampedForBrokenStoredStep() {
        assertEquals(SIZE_STEP_MULTIPLIERS.first(), AppearanceSettings(sizeStep = -5).sizeMultiplier, 0f)
        assertEquals(SIZE_STEP_MULTIPLIERS.last(), AppearanceSettings(sizeStep = 99).sizeMultiplier, 0f)
    }

    @Test
    fun sizeMultipliersGrowWithStep() {
        assertTrue(SIZE_STEP_MULTIPLIERS.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun accentsAreSixWithUniqueIdsAndFallbackToCyan() {
        assertEquals(6, AccentColor.entries.size)
        assertEquals(6, AccentColor.entries.map { it.id }.toSet().size)
        assertEquals(AccentColor.CYAN, AccentColor.fromId(null))
        assertEquals(AccentColor.CYAN, AccentColor.fromId("unknown"))
        AccentColor.entries.forEach { assertEquals(it, AccentColor.fromId(it.id)) }
    }

    @Test
    fun defaultAccentIsTheBrandColor() {
        assertEquals(ZenithPrimary, AccentColor.CYAN.color)
    }

    @Test
    fun imageScaleAndTvLayoutFallBackToDefaults() {
        assertEquals(ImageScaleMode.AUTO, ImageScaleMode.fromId(null))
        assertEquals(ImageScaleMode.AUTO, ImageScaleMode.fromId("???"))
        assertEquals(TvLayout.GRID, TvLayout.fromId(null))
        assertEquals(TvLayout.GRID, TvLayout.fromId("???"))
        assertEquals(ImageScaleMode.FIT, ImageScaleMode.fromId("fit"))
        assertEquals(TvLayout.CAROUSEL, TvLayout.fromId("carousel"))
    }

    @Test
    fun cardStyleUnknownIdMeansNotSetSoSectionDefaultApplies() {
        assertNull(CardStyle.fromId(null))
        assertNull(CardStyle.fromId("???"))
        assertEquals(CardStyle.WIDE, CardStyle.fromId("wide"))
    }

    @Test
    fun sectionDefaultsKeepThePreviousLook() {
        val s = AppearanceSettings()
        assertEquals(CardStyle.POSTER, s.styleOf(CardSection.MOVIES))
        assertEquals(CardStyle.POSTER, s.styleOf(CardSection.FAVORITES))
        assertEquals(CardStyle.LIST, s.styleOf(CardSection.HISTORY))
        // Каналы: на ТВ были плитки, на телефоне строки.
        assertEquals(CardStyle.POSTER, s.styleOf(CardSection.CHANNELS, isTv = true))
        assertEquals(CardStyle.LIST, s.styleOf(CardSection.CHANNELS, isTv = false))
    }

    @Test
    fun explicitChoiceWinsOverDefaultOnBothPlatforms() {
        val s = AppearanceSettings(cardStyles = mapOf(CardSection.CHANNELS to CardStyle.WIDE))
        assertEquals(CardStyle.WIDE, s.styleOf(CardSection.CHANNELS, isTv = true))
        assertEquals(CardStyle.WIDE, s.styleOf(CardSection.CHANNELS, isTv = false))
        assertEquals(CardStyle.POSTER, s.styleOf(CardSection.MOVIES))
    }

    @Test
    fun channelsCallPosterATile() {
        assertEquals("Плитка", CardStyle.POSTER.titleFor(CardSection.CHANNELS))
        assertEquals("Постер", CardStyle.POSTER.titleFor(CardSection.MOVIES))
        assertEquals("Список", CardStyle.LIST.titleFor(CardSection.CHANNELS))
    }

    @Test
    fun sectionIdsAreUniqueBecauseTheyAreStoredInPreferences() {
        val ids = CardSection.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
