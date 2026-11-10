package com.platinum.ott.presentation.screens.detail
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.Recommendation
import com.platinum.ott.domain.model.TmdbMetadata
sealed interface DetailUiState { object Loading : DetailUiState
    // recommendations — PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5. Отдельное
    // поле, не часть TmdbMetadata: не кэшируется в Room (см. TmdbRepository.kt),
    // грузится и приходит в state независимо, дефолт пустой список — не
    // ломает существующие места, где Success создаётся без явного указания.
    data class Success(val movie: Movie, val metadata: TmdbMetadata? = null, val isFavorite: Boolean = false, val watchProgress: Float? = null, val isAnime: Boolean = false, val recommendations: List<Recommendation> = emptyList(), val series: SeriesInfo? = null, val tmdbOnly: Boolean = false) : DetailUiState
    data class Error(val message: String) : DetailUiState
    // Раньше каждый эпизод сериала из М3У/Xtream-каталога (movie.seriesId != null)
    // открывал СВОЮ обычную детальную карточку — постер/описание/кнопка
    // "Смотреть" ровно как у отдельного фильма, хотя реально это одна серия
    // из сериала. Единая точка редиректа здесь — не в каждом месте, откуда
    // можно попасть на "detail/{id}" (лента, поиск, история) по отдельности,
    // это было бы легко забыть продублировать при следующей точке входа.
    data class RedirectToSeries(val seriesId: String) : DetailUiState
}

/** Данные сериала для единого окна «Деталь»: серии, прогресс по каждой и куда вести кнопку «Смотреть». */
data class SeriesInfo(
    val episodes: List<Movie>,
    val history: Map<String, WatchHistoryEntity>,
    val resume: SeriesResume
)

enum class ResumeKind { START, CONTINUE, NEXT }

data class SeriesResume(val episode: Movie, val kind: ResumeKind, val progress: Float?)
