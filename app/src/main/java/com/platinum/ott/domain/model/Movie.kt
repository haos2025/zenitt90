package com.platinum.ott.domain.model

data class Movie(val id: String, val year: Int, val title: String, val poster: String,
    val description: String = "", val genre: String = "", val duration: String = "",
    val rating: Double = 0.0, val streamUrl: String = "")
