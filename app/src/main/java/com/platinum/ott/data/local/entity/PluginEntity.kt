package com.platinum.ott.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val installedVersion: String = version,
    val description: String = "",
    val author: String = "",
    val pluginType: String = "source",
    val iconUrl: String? = null,
    val repoUrl: String = "",
    val scriptContent: String = "",
    val isEnabled: Boolean = true,
    val priority: Int = 100,
    val settings: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
