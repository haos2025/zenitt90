package com.platinum.ott.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CatalogResponseDto(
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
    val totalItems: Int = 0, // backend (CatalogResponseOut) не отдаёт это поле — всегда останется 0
    val items: List<MovieDto> = emptyList()
)
