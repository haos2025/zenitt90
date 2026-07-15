package com.platinum.ott.domain.model

data class CatalogPage(val movies: List<Movie>, val currentPage: Int, val totalPages: Int, val totalItems: Int)
