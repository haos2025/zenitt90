package com.platinum.ott.domain.model

data class NextEpisode(
    val airDateEpochMs: Long,
    val seasonNum: Int,
    val episodeNum: Int,
    val episodeName: String
)
