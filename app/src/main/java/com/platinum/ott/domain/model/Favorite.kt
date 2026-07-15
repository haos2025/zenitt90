package com.platinum.ott.domain.model

data class Favorite(val contentId: String, val folderId: Long? = null, val contentType: String = "MOVIE",
    val title: String = "", val poster: String? = null, val addedAt: Long = System.currentTimeMillis())
