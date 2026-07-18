package com.platinum.ott.data.remote.dto

import com.google.gson.annotations.SerializedName

// backend (ScriptManifestEntry) шлёт version как JSON-число — Gson лениво
// приводит NUMBER-токен в String-поле через JsonReader.nextString(), падения
// не будет, но при желании можно сменить version на Int для строгости.
data class ScriptManifestDto(
    val name: String = "",
    val version: String = "",
    @SerializedName("hash") val sha256: String = ""
)
