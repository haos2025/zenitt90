package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

// Раньше в SettingsScreen.kt было три захардкоженные строки "Качество по
// умолчанию"/"Автовоспроизведение"/"Субтитры", и для двух из них — авто-
// воспроизведение следующей серии и субтитры — не было НИКАКОЙ реализации
// вообще (ни "следующего эпизода" как понятия, ни обработки субтитровых
// дорожек), поэтому их убрали как выдуманные настройки под несуществующие
// фичи. Теперь и то и другое реализовано (см. PlayerViewModel:
// playNextEpisode()/playPreviousEpisode(), TrackOption/selectSubtitleTrack) —
// значит настройка снова осмысленна, отсюда этот класс, по аналогии с
// QualityPreferences.
class SubtitlePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_subtitles", Context.MODE_PRIVATE)

    // По умолчанию — false: у ExoPlayer/DefaultTrackSelector собственное
    // системное поведение (CaptioningManager) само включает субтитры, если
    // у пользователя в системе включены спец. возможности — сохраняем этот
    // привычный дефолт Android, а не навязываем свой.
    fun getShowByDefault(): Boolean = prefs.getBoolean("show_by_default", false)
    fun setShowByDefault(enabled: Boolean) = prefs.edit().putBoolean("show_by_default", enabled).apply()
}
