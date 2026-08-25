package com.platinum.ott.domain.repository

import com.platinum.ott.domain.model.NextEpisode
import com.platinum.ott.domain.model.Recommendation
import com.platinum.ott.domain.model.TmdbMetadata

interface TmdbRepository {
    suspend fun getMetadata(contentId: String, title: String, year: Int? = null): Result<TmdbMetadata>
    // Раньше не было способа спросить "когда следующая серия" отдельно от
    // общих метаданных фильма — этим и должен был пользоваться
    // SeriesTrackerUseCase, но там был placeholder-комментарий вместо кода.
    suspend fun getNextEpisode(seriesTitle: String): NextEpisode?
    // PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5 — сознательно НЕ часть
    // getMetadata()/не кэшируется в Room: это лёгкий живой запрос на время
    // открытого экрана деталки (один фильм за раз, не 20-30 карточек ленты
    // разом, как у getMetadata), не хранить его смысла нет. Пустой список
    // при tmdbId == null или любой ошибке сети — вызывающая сторона просто
    // не рисует блок "Смотрите также", не показывает ошибку.
    suspend fun getRecommendations(tmdbId: Int): List<Recommendation>
}
