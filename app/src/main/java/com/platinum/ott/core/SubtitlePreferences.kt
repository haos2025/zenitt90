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

    // PROMPT_SUBTITLES.md, подзадача 9 — "ползунок скорость/точность...
    // для тех, кому точность важнее скорости (сценарий нарушений слуха),
    // должен быть выбор, не один компромисс на всех". Влияет только на
    // локальный Whisper-фолбэк (подзадача 5/6) — облачный путь всегда
    // использует то, что настроено на backend, выбор модели там не имеет
    // смысла. По умолчанию — скорость (false): большинству пользователей
    // это удобнее на слабых TV-чипах, точность — осознанный выбор, не дефолт.
    fun getPreferLocalAccuracy(): Boolean = prefs.getBoolean("prefer_local_accuracy", false)
    fun setPreferLocalAccuracy(preferAccuracy: Boolean) = prefs.edit().putBoolean("prefer_local_accuracy", preferAccuracy).apply()
}
