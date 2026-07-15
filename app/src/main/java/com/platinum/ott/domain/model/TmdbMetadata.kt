package com.platinum.ott.domain.model

data class TmdbMetadata(val tmdbId: Int? = null, val posterPath: String? = null, val backdropPath: String? = null,
    val overview: String? = null, val voteAverage: Double? = null, val genres: String? = null,
    val trailerUrl: String? = null, val cast: String? = null)
