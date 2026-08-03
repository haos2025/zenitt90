package com.platinum.ott.domain.repository

import com.platinum.ott.domain.model.NextEpisode
import com.platinum.ott.domain.model.TmdbMetadata

interface TmdbRepository {
    suspend fun getMetadata(contentId: String, title: String, year: Int? = null): Result<TmdbMetadata>
    // Раньше не было способа спросить "когда следующая серия" отдельно от
    // общих метаданных фильма — этим и должен был пользоваться
    // SeriesTrackerUseCase, но там был placeholder-комментарий вместо кода.
    suspend fun getNextEpisode(seriesTitle: String): NextEpisode?
}
