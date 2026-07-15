package com.platinum.ott.data.remote.dto

data class CatalogResponseDto(val page: Int = 1, val totalPages: Int = 1, val totalItems: Int = 0, val items: List<MovieDto> = emptyList())
