package com.platinum.ott.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CatalogResponseDto(
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
    val totalItems: Int = 0, // backend (CatalogResponseOut) не отдаёт это поле — всегда 0.
    // Раньше пагинация (HomeViewModel.loadMore()) считала totalPages именно
    // из этого поля и из-за этого была сломана целиком — см.
    // MovieRepositoryImpl.getCatalog(), там используется totalPages ниже,
    // а не это поле.
    val items: List<MovieDto> = emptyList()
)
