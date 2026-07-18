package com.platinum.ott.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MovieDto(val id: String = "", val title: String = "", val year: Int = 0,
    @SerializedName("poster_url") val poster: String? = null,
    val description: String? = null, val genre: String? = null,
    val duration: String? = null, val rating: Double? = null, val streamUrl: String? = null)
